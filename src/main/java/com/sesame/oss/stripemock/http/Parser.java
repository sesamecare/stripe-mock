package com.sesame.oss.stripemock.http;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public class Parser {
    Map<String, Object> parseRequestBody(String requestBody, String contentType) throws ResponseCodeException {
        if ("application/x-www-form-urlencoded;charset=UTF-8".equals(contentType)) {
            return parseFormData(requestBody);
        } else if (contentType == null) {
            // This happens for all GET calls
            return null;
        } else {
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    private Map<String, Object> parseFormData(String requestBody) throws ResponseCodeException {
        String[] parameters = requestBody.split("&");
        Map<String, Object> values = new HashMap<>();
        for (String parameter : parameters) {
            if (parameter.isBlank()) {
                continue;
            }
            // This has values like this: cash_balance[settings][reconciliation_mode]=automatic
            String[] parameterParts = parameter.split("=");
            String key = parameterParts[0];
            String value;
            if (parameterParts.length == 1) {
                // This happens for calls to putMetadata("value", null), which we must support
                value = "";
            } else {
                value = parameterParts[1];
            }

            // Maybe there's a more elegant way to do this
            String[] keyPath = key.split("\\[");
            Map<String, Object> parentValue = values;
            for (int i = 0; i < keyPath.length; i++) {
                String keyPathPart = keyPath[i];
                if (keyPathPart.endsWith("]")) {
                    keyPathPart = keyPathPart.substring(0, keyPathPart.length() - 1);
                }
                if (i == keyPath.length - 1) {
                    // This is the last one. Store the value instead of the next map
                    parentValue.put(keyPathPart, toJsonParseableType(value));
                } else {
                    parentValue = (Map<String, Object>) parentValue.computeIfAbsent(keyPathPart, ignored -> new HashMap<>());
                }
            }
        }
        return convertIndexedMapsToArrays(values);
    }

    /**
     * It would be super nice if gson just handled these indexed arrays, and maybe it does, but it doesn't by default, so we have to do stuff like this.
     * It's easier to do it on the existing map, rather than as we're creating the map, as we don't have to look forward to the next token
     */
    private Map<String, Object> convertIndexedMapsToArrays(Map<String, Object> input) {
        return input.entrySet()
                    .stream()
                    .map(entry -> {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        Object newValue;
                        if (value instanceof Map valueAsUntypedMap) {
                            Map<String, Object> valueMap = valueAsUntypedMap;
                            // if the first key is a number, we're going to assume that they all are, and that this is supposed to be a list
                            OptionalInt maxIndex = valueMap.keySet()
                                                           .stream()
                                                           .mapToInt(potentialIntegerKey -> {
                                                               try {
                                                                   return Integer.parseInt(potentialIntegerKey);
                                                               } catch (NumberFormatException e) {
                                                                   return -1;
                                                               }
                                                           })
                                                           .max();
                            if (maxIndex.isPresent() && maxIndex.getAsInt() > -1) {
                                Object[] stripeCollectionData = new Object[maxIndex.getAsInt() + 1];
                                // We know all entries are ints if we enter this block
                                for (Map.Entry<String, Object> intEntry : valueMap.entrySet()) {
                                    stripeCollectionData[Integer.parseInt(intEntry.getKey())] = switch (intEntry.getValue()) {
                                        case Map arrayItemMap -> convertIndexedMapsToArrays(arrayItemMap);
                                        case Object o -> o;
                                    };
                                }


                                // Lists of primitives should just be an array, but list of complex objects should be a stripe collection
                                // We assume that if one's a map, the other ones are too
                                if (stripeCollectionData[0] instanceof Map) {
                                    Map<String, Object> stripeCollection = new HashMap<>();
                                    stripeCollection.put("object", "list");
                                    stripeCollection.put("data", stripeCollectionData);
                                    newValue = stripeCollection;
                                } else {
                                    newValue = stripeCollectionData;
                                }

                            } else {
                                newValue = convertIndexedMapsToArrays(valueMap);
                            }
                        } else {
                            newValue = value;
                        }
                        return Map.entry(key, newValue);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey,
                                              Map.Entry::getValue,
                                              (a, b) -> {throw new IllegalStateException("There should be no duplicates");},
                                              HashMap::new));
    }

    private Object toJsonParseableType(String value) {
        // It turns out that gson is smart enough to handle the case when everything is a string, so we don't need to do any actual type conversion here
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
