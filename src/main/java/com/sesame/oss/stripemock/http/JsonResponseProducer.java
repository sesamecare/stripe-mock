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

    String toJson(List<?> values, Map<String, Object> requestBodyFormData, QueryParameters queryParameters, String url) throws ResponseCodeException {
        if (values == null) {
            //language=json
            return String.format("""
                                 {
                                  "object": "list",
                                  "url": "%s",
                                  "has_more": false,
                                  "total_count": 0,
                                  "data": []
                                 }
                                 """, url);
        } else {
            List<String> expandPaths = getExpandPaths(requestBodyFormData, queryParameters);
            JsonObject root = new JsonObject();
            root.addProperty("object", "list");
            root.addProperty("has_more", false);
            root.addProperty("url", url);
            root.addProperty("total_count", values.size());
            JsonArray data = new JsonArray();
            root.add("data", data);
            if (expandPaths.isEmpty()) {
                for (Object value : values) {
                    data.add(Utilities.PRODUCER_GSON.toJsonTree(value));
                }
            } else {
                for (Object value : values) {
                    data.add(expand(value, expandPaths));
                }
            }
            return Utilities.PRODUCER_GSON.toJson(root);
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
                if (i == 0 && "data".equals(pathPart)) {
                    // This is really only if this is a list, but afaik know other root level elements contain a 'data' field, so this is fine for now.
                    continue;
                }
                JsonElement expandableFieldIdOrNull = parent.remove(pathPart);
                if (expandableFieldIdOrNull == null) {
                    if ("data".equals(pathParts[0])) {
                        // It seems like this is lenient when dealing with lists.
                        // For balance transactions, when the list contains a mix of things, Stripe will simply ignore
                        // fields that don't exist in the underlying source.
                        // See com.sesame.oss.stripemock.BalanceTransactionTest.shouldListWithExpansions
                        break;
                    }
                    throw new ResponseCodeException(400,
                                                    String.format("This property cannot be expanded (%s).",
                                                                  Arrays.stream(pathParts)
                                                                        .limit(i + 1)
                                                                        .collect(Collectors.joining("."))));
                }
                JsonElement expandedObject;
                if (expandableFieldIdOrNull.isJsonObject()) {
                    JsonObject expandableField = expandableFieldIdOrNull.getAsJsonObject();
                    if (isList(expandableField)) {
                        JsonArray data = expandableField.getAsJsonArray("data");
                        if (data.isEmpty()) {
                            // We currently have the lists right in the main object for all our entities.
                            // While that might change in the future, for now we can actually just return the list itself.
                            // If it's empty when we get here, it'll be empty in the object too.
                            // We should keep this branch, though, to make it clear what the distinction is in the future.
                            expandedObject = expandableField;
                        } else {
                            // This was already full of data and expanded, so restore the state
                            expandedObject = expandableField;
                        }
                    } else {
                        // todo: what do we actually need to do here?
                        expandedObject = expandableField;
                    }
                } else {
                    String expandableFieldId = expandableFieldIdOrNull.getAsString();
                    Object entity = stripeEntities.getEntityById(expandableFieldId)
                                                  .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, pathPart, expandableFieldId));

                    expandedObject = Utilities.PRODUCER_GSON.toJsonTree(entity);
                }
                parent.add(pathPart, expandedObject);
                parent = expandedObject.getAsJsonObject();
            }
        }
        return root;
    }

    private boolean isList(JsonObject object) {
        return object.has("object") &&
               object.getAsJsonPrimitive("object")
                     .isString() &&
               object.getAsJsonPrimitive("object")
                     .getAsString()
                     .equals("list");
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
