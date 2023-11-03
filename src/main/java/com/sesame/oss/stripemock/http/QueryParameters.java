package com.sesame.oss.stripemock.http;

import java.util.*;

public class QueryParameters {
    private final String wholeQueryParameterString;
    private final Map<String, List<String>> keyValuePairs = new HashMap<>();

    public QueryParameters(String wholeQueryParameterString) {
        this.wholeQueryParameterString = wholeQueryParameterString;
        if (wholeQueryParameterString != null) {
            for (String pair : wholeQueryParameterString.split("&")) {
                String[] pairParts = pair.split("=");
                List<String> values = keyValuePairs.computeIfAbsent(pairParts[0], ignored -> new ArrayList<>());
                if (pairParts.length == 2) {
                    values.add(pairParts[1]);
                }
            }
        }
    }

    public Collection<String> getParameters() {
        return Collections.unmodifiableSet(keyValuePairs.keySet());
    }

    public Optional<String> getFirst(String key) {
        List<String> values = keyValuePairs.get(key);
        if (values == null) {
            return Optional.empty();
        }
        if (!values.isEmpty()) {
            return Optional.ofNullable(values.getFirst());
        } else {
            return Optional.empty();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QueryParameters that = (QueryParameters) o;
        return Objects.equals(wholeQueryParameterString, that.wholeQueryParameterString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(wholeQueryParameterString);
    }

    @Override
    public String toString() {
        return wholeQueryParameterString == null ? "" : "?" + wholeQueryParameterString;
    }
}
