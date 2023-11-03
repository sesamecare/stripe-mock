package com.sesame.oss.stripemock.http;

import com.sesame.oss.stripemock.util.Utilities;
import com.sun.net.httpserver.Headers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This uses the Headers from the built-in HttpServer. They're actually not immutable, so they're not a good data structure, but they'll have to do for now.
 */
class IdempotencyManager {
    private final Lock lock = new ReentrantLock();
    public static final IdempotentRequest CALL_DIRECTLY = NonThrowingCallable::call;
    private final Map<String, Request> requests = new HashMap<>();

    public IdempotentRequest start(String idempotencyKey,
                                   String method,
                                   QueryParameters queryParameters,
                                   String requestBody,
                                   Headers requestHeaders,
                                   String requestId) throws ResponseCodeException {
        if (idempotencyKey == null || !"POST".equals(method)) {
            return CALL_DIRECTLY;
        }
        lock.lock();
        try {
            Request existingRequest = requests.get(idempotencyKey);
            if (existingRequest == null) {
                Request newRequest = new Request(requestBody, requestHeaders, queryParameters);
                requests.put(idempotencyKey, newRequest);
                return new IdempotentRequest() {
                    @Override
                    public RawResponse finish(NonThrowingCallable<RawResponse> processor) {
                        // This lock right here makes ALL idempotent requests single-threaded, as they all wait for THIS LOCK.
                        // We can probably do better, but it's also probably not needed for normal testing.
                        lock.lock();
                        try {
                            RawResponse response = processor.call();
                            newRequest.setResponse(response);
                            return response;
                        } catch (Throwable e) {
                            RawResponse response = new RawResponse(500,
                                                                   Utilities.toApiError(e.getMessage(), null, null, null),
                                                                   Utilities.defaultHeaders(idempotencyKey, requestId),
                                                                   requestId);
                            newRequest.setResponse(response);
                            return response;
                        } finally {
                            lock.unlock();
                        }
                    }
                };
            } else {
                if (existingRequest.isIncomplete()) {
                    // We could just pause the processing, and return as soon as it is completed, but that's too complicated for now.
                    throw new ResponseCodeException(429, "The original request hasn't completed yet. Please try again.");
                }
                if (existingRequest.matchesInput(requestBody, queryParameters, requestHeaders)) {
                    return new IdempotentRequest() {
                        @Override
                        public RawResponse finish(NonThrowingCallable<RawResponse> processor) {
                            return existingRequest.getResponse();
                        }
                    };
                } else {
                    return processor -> {
                        //language=json
                        String body = String.format("""
                                                    {
                                                      "error": {
                                                          "code": "idempotency_key_in_use",
                                                          "type": "idempotency_error",
                                                          "message": "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request."
                                                        }
                                                    }
                                                    """, idempotencyKey);
                        return new RawResponse(400, body, Utilities.defaultHeaders(idempotencyKey, requestId), requestId);
                    };
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private static final class Request {
        private final String requestBody;
        private final Headers requestHeaders;
        private final QueryParameters queryParameters;
        private RawResponse response;

        public Request(String requestBody, Headers requestHeaders, QueryParameters queryParameters) {
            this.requestBody = requestBody;
            this.queryParameters = queryParameters;
            this.requestHeaders = new Headers();
            this.requestHeaders.putAll(requestHeaders);
            this.requestHeaders.remove("X-stripe-client-telemetry");
        }

        public boolean isIncomplete() {
            return response == null;
        }

        public void setResponse(RawResponse response) {
            this.response = response;
        }

        public RawResponse getResponse() {
            return response;
        }

        public boolean matchesInput(String requestBody, QueryParameters queryParameters, Headers requestHeaders) {
            Headers comparableRequestHeaders = new Headers();
            comparableRequestHeaders.putAll(requestHeaders);
            comparableRequestHeaders.remove("X-stripe-client-telemetry");
            return this.requestBody.equals(requestBody) && this.requestHeaders.equals(comparableRequestHeaders) && this.queryParameters.equals(queryParameters);
        }
    }

    interface IdempotentRequest {
        public RawResponse finish(NonThrowingCallable<RawResponse> processor);
    }

    interface NonThrowingCallable<T> {
        T call();
    }

}
