package com.sesame.oss.stripemock.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sesame.oss.stripemock.entities.StripeEntities;
import com.sesame.oss.stripemock.util.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class JsonResponseProducer {
    private final StripeEntities stripeEntities;

    JsonResponseProducer(StripeEntities stripeEntities) {
        this.stripeEntities = stripeEntities;
    }

    // todo: what happens in stripe if we expand an object that doesn't yet have an id set? Is it just ignored?

    String toJson(Object responseBody, Map<String, Object> requestBodyFormData, QueryParameters queryParameters) throws ResponseCodeException {

        if (responseBody == null) {
            return "";
        }
        List<String> expandPaths = getExpandPaths(requestBodyFormData, queryParameters);
        if (expandPaths.isEmpty()) {
            return Utilities.PRODUCER_GSON.toJson(responseBody);
        } else {
            return Utilities.PRODUCER_GSON.toJson(expand(responseBody, expandPaths));
        }
    }

    String toJson(List<?> values, Map<String, Object> requestBodyFormData, QueryParameters queryParameters) throws ResponseCodeException {
        if (values == null) {
            //language=json
            return """
                   {
                    "type": "list",
                    "data": []
                   }
                   """;
        } else {
            List<String> expandPaths = getExpandPaths(requestBodyFormData, queryParameters);
            if (expandPaths.isEmpty()) {
                return Utilities.PRODUCER_GSON.toJson(new Utilities.StripeList<>(values, false));
            } else {
                JsonObject root = new JsonObject();
                root.addProperty("type", "list");
                JsonArray data = new JsonArray();
                root.add("data", data);
                for (Object value : values) {
                    data.add(expand(value, expandPaths));
                }
                return Utilities.PRODUCER_GSON.toJson(root);
            }
        }
    }

    private JsonObject expand(Object value, List<String> pathsToExpand) throws ResponseCodeException {
        JsonObject root = Utilities.PRODUCER_GSON.toJsonTree(value)
                                                 .getAsJsonObject();
        for (String pathToExpand : pathsToExpand) {
            String[] pathParts = pathToExpand.split("\\.");
            JsonObject parent = root;
            for (int i = 0; i < pathParts.length; i++) {
                String pathPart = pathParts[i];
                JsonElement expandableFieldIdOrNull = parent.remove(pathPart);
                if (expandableFieldIdOrNull == null) {
                    throw new ResponseCodeException(400,
                                                    String.format("This property cannot be expanded (%s).",
                                                                  Arrays.stream(pathParts)
                                                                        .limit(i + 1)
                                                                        .collect(Collectors.joining("."))));
                }
                String expandableFieldId = expandableFieldIdOrNull.getAsString();
                Object entity = stripeEntities.getEntityById(expandableFieldId)
                                              .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, pathPart, expandableFieldId));
                JsonElement expandedObject = Utilities.PRODUCER_GSON.toJsonTree(entity);
                parent.add(pathPart, expandedObject);
                parent = expandedObject.getAsJsonObject();
            }
        }
        return root;
    }

    private List<String> getExpandPaths(Map<String, Object> requestBodyFormData, QueryParameters queryParameters) {
        // It's unlikely that they'd be provided in both of these at the same time, but support it anyway
        List<String> expandPaths = new ArrayList<>();
        if (requestBodyFormData != null && requestBodyFormData.get("expand") instanceof Object[] pathsToExpand) {
            for (Object path : pathsToExpand) {
                expandPaths.add((String) path);
            }
        }
        Pattern expandQueryParameterPattern = Pattern.compile("expand\\[\\d+]");
        for (String parameter : queryParameters.getParameters()) {
            if (expandQueryParameterPattern.matcher(parameter)
                                           .matches()) {
                expandPaths.add(queryParameters.getFirst(parameter)
                                               .orElseThrow());
            }
        }
        return expandPaths;
    }
}
