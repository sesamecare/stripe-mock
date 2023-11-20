package com.sesame.oss.stripemock.util;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ReflectionAccessFilter;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.net.ApiResource;
import com.sun.net.httpserver.Headers;

import java.util.List;
import java.util.Random;

public class Utilities {
    // @formatter:off
    private static final char[] ALPHABET = new char[]{'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','x','y','z',
                                                      'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','X','Y','Z',
                                                      '0','1','2','3','4','5','6','7','8','9'};
    //@formatter:on
    private static final Random RANDOM = new Random();
    public static final Gson PRODUCER_GSON = producerGson();


    public static String randomIdWithPrefix(String prefix, int length) {
        return prefix + "_" + randomStringOfLength(length);
    }

    public static String randomStringOfLength(int length) {
        char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length - 1)];
        }
        return new String(chars);
    }

    public static String snakeCase(String camelCase) {
        // Shamelessly stolen from hibernate-types
        String camelCaseRegexp = "([a-z]+)([A-Z]+)";
        String snakeCasePattern = "$1\\_$2";
        return camelCase.replaceAll(camelCaseRegexp, snakeCasePattern)
                        .toLowerCase();
    }

    /**
     * This is very similar to {@link ApiResource#createGson()}, with the exception that it does not include the support for some interfaces.
     * The reason for this is that the handler for those interfaces doesn't have any fields. As such, when we want to <b>write</b> json, which
     * it is not really configured for, we get empty fields. So we have to create our own.
     */
    private static Gson producerGson() {
        return new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                                .registerTypeAdapter(EphemeralKey.class, new EphemeralKeyDeserializer())
                                .registerTypeAdapter(Event.Data.class, new EventDataDeserializer())
                                .registerTypeAdapter(Event.Request.class, new EventRequestDeserializer())
                                .registerTypeAdapter(ExpandableField.class, new ExpandableFieldDeserializer())
                                // Taken from StripeObject.PRETTY_PRINT_GSON. It turns expandable fields with nothing to expand into normal strings
                                .registerTypeAdapter(ExpandableField.class, new ExpandableFieldSerializer())
                                .registerTypeAdapter(StripeRawJsonObject.class, new StripeRawJsonObjectDeserializer())
                                .addReflectionAccessFilter(new ReflectionAccessFilter() {
                                    @Override
                                    public FilterResult check(Class<?> rawClass) {
                                        String typeName = rawClass.getTypeName();
                                        if (typeName.startsWith("com.stripe.") || typeName.startsWith("com.sesame.oss.stripemock.util.Utilities$")) {
                                            return FilterResult.ALLOW;
                                        }
                                        return FilterResult.BLOCK_ALL;
                                    }
                                })
                                .create();
    }

    public static Headers defaultHeaders(String idempotencyKey, String requestId) {
        Headers headers = new Headers();
        headers.set("Content-Type", "application/json");
        headers.set("Request-Id", requestId);
        headers.set("Stripe-Version", Stripe.API_VERSION);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    public static String toApiError(String message, String code, String type, String declineCode) {
        String sanitizedMessage;
        if (message != null) {
            sanitizedMessage = message.replaceAll("\"", "\\\\\"")
                                      .replaceAll("\n", "");
        } else {
            sanitizedMessage = "";
        }
        return PRODUCER_GSON.toJson(new StripeError(new StripeError.Error(sanitizedMessage, code, type, declineCode)));
    }

    public static String toApiError(ResponseCodeException e) {
        return toApiError(e.getMessage(), e.getCode(), e.getErrorType(), e.getDeclineCode());
    }

    public record StripeList<T>(List<T> data,
                                boolean hasMore) {
        public String getObject() {
            // used by GSON
            return "list";
        }
    }

    public record StripeError(Error error) {
        public record Error(String message,
                            String code,
                            String type,
                            String declineCode) {

        }
    }
}
