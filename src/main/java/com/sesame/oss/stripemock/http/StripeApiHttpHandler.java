package com.sesame.oss.stripemock.http;

import com.sesame.oss.stripemock.StripeMock;
import com.sesame.oss.stripemock.entities.StripeEntities;
import com.sesame.oss.stripemock.http.EntityResponse.Multiple;
import com.sesame.oss.stripemock.http.EntityResponse.Single;
import com.sesame.oss.stripemock.util.Utilities;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StripeApiHttpHandler implements HttpHandler {
    private final IdempotencyManager idempotencyManager = new IdempotencyManager();
    private final Parser parser = new Parser();

    private final JsonResponseProducer jsonResponseProducer;
    private final EntityRequestHandler requestHandler;

    public StripeApiHttpHandler(StripeEntities stripeEntities) {
        this.jsonResponseProducer = new JsonResponseProducer(stripeEntities);
        this.requestHandler = new EntityRequestHandler(stripeEntities);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestId = Utilities.randomIdWithPrefix("req", 14);
        String method = exchange.getRequestMethod();
        URI requestURI = exchange.getRequestURI();
        String query = requestURI.getQuery();
        Headers requestHeaders = exchange.getRequestHeaders();
        String requestBody = readInputFully(exchange);

        if (StripeMock.isLogRequests()) {
            String message = """
                                                          
                             Request: %s %s%s
                             Headers: %s
                             Body: %s
                             Request-Id: %s
                             """;
            Logger.getLogger("stripe-mock-requests")
                  .log(Level.INFO, String.format(message, method, requestURI, query == null ? "" : query, requestHeaders, requestBody, requestId));
        }

        RawResponse rawResponse = processRequest(requestURI, query, requestHeaders, method, requestBody, requestId);
        Headers responseHeaders = sendResponse(exchange, rawResponse, requestId);

        if (StripeMock.isLogRequests()) {
            String message = """
                                                          
                             Response to request: %s
                             Code: %d
                             Headers: %s
                             Body: %s
                             """;
            Logger.getLogger("stripe-mock-responses")
                  .log(Level.INFO, String.format(message, requestId, rawResponse.code(), responseHeaders, rawResponse.body()));
        }
    }

    private Headers sendResponse(HttpExchange exchange, RawResponse rawResponse, String requestId) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.putAll(rawResponse.headers());
        if (!requestId.equals(rawResponse.requestId())) {
            // If the request ids are different in the request and the response, this was a replay of an idempotent request
            // todo: ideally we'd like to do all of this in the IdempotencyManager
            responseHeaders.add("Original-Request", rawResponse.requestId());
            responseHeaders.add("Idempotent-Replayed", "true");
        }
        byte[] responseBodyBytes = rawResponse.body()
                                              .getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(rawResponse.code(), responseBodyBytes.length);
        try (OutputStream responseBody = exchange.getResponseBody()) {
            responseBody.write(responseBodyBytes);
            responseBody.flush();
        }
        return responseHeaders;
    }

    private RawResponse processRequest(URI requestURI, String query, Headers requestHeaders, String method, String requestBody, String requestId) {
        try {
            String[] path = requestURI.getPath()
                                      .split("/");
            QueryParameters queryParameters = new QueryParameters(query);

            String idempotencyKey = requestHeaders.getFirst("Idempotency-Key");
            return idempotencyManager.start(idempotencyKey, method, queryParameters, requestBody, requestHeaders, requestId)
                                     .finish(() -> {
                                         // This body here MUST NOT throw any exceptions. If it does, the idempotent request remains unfinished forever.
                                         // Thus, we have to catch here and produce a response, even if it's a broken one.


                                         // This is only called if we didn't already have a response for this request+idempotency key
                                         Headers responseHeaders = Utilities.defaultHeaders(idempotencyKey, requestId);
                                         try {
                                             Map<String, Object> requestBodyFormData =
                                                     parser.parseRequestBody(requestBody, requestHeaders.getFirst("Content-Type"));
                                             EntityResponse response =
                                                     requestHandler.handleRequest(method, path, queryParameters, requestHeaders, requestBodyFormData);
                                             return switch (response) {
                                                 case Single(int code, Object entity) -> new RawResponse(code,
                                                                                                         jsonResponseProducer.toJson(entity,
                                                                                                                                     requestBodyFormData,
                                                                                                                                     queryParameters),
                                                                                                         responseHeaders,
                                                                                                         requestId);
                                                 case Multiple(int code, List<?> entities) -> new RawResponse(code,
                                                                                                              jsonResponseProducer.toJson(entities,
                                                                                                                                          requestBodyFormData,
                                                                                                                                          queryParameters,
                                                                                                                                          requestURI.getPath()),
                                                                                                              responseHeaders,
                                                                                                              requestId);
                                             };
                                         } catch (ResponseCodeException e) {
                                             return new RawResponse(e.getResponseCode(), Utilities.toApiError(e), responseHeaders, requestId);
                                         } catch (Throwable e) {
                                             Logger.getLogger("stripe-mock")
                                                   .log(Level.SEVERE, "Could not process response", e);
                                             return new RawResponse(500,
                                                                    Utilities.toApiError(e.getMessage(), null, null, null, null),
                                                                    responseHeaders,
                                                                    requestId);
                                         }
                                     });
        } catch (Throwable e) {
            Logger.getLogger("stripe-mock")
                  .log(Level.SEVERE, "Could not process request", e);
            return new RawResponse(500, Utilities.toApiError(e.getMessage(), null, null, null, null), Utilities.defaultHeaders(null, requestId), requestId);
        }
    }

    private String readInputFully(HttpExchange exchange) throws IOException {
        try (InputStream requestBody = exchange.getRequestBody()) {
            byte[] bytes = requestBody.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
