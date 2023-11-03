package com.sesame.oss.stripemock.http;

import com.sesame.oss.stripemock.entities.EntityManager;
import com.stripe.model.HasId;
import com.stripe.net.ApiResource;

import java.util.Map;
import java.util.Objects;

class EntityRequestHandler<T extends ApiResource & HasId> {
    private final EntityManager<T> entityManager;

    EntityRequestHandler(EntityManager<T> entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager);
    }

    public TypedResponse<T> handleRequest(String method, String[] path, QueryParameters query, Map<String, Object> nestedFormParameters)
            throws ResponseCodeException {
        return switch (method) {
            case "GET" -> switch (path.length) {
                case 3 -> new TypedResponse.List<>(200, entityManager.list(query));
                case 4 -> entityManager.get(path[3])
                                       .map(resource -> new TypedResponse.Single<>(200, resource))
                                       .orElseThrow(() -> noSuchEntityException(path));
                default -> throw new ResponseCodeException(400, "Unsupported path length");
            };
            case "POST" -> switch (path.length) {
                case 3 -> new TypedResponse.Single<>(200, entityManager.add(nestedFormParameters));
                case 4 -> entityManager.update(path[3], nestedFormParameters)
                                       .map(entity -> new TypedResponse.Single<>(200, entity))
                                       .orElseThrow(() -> noSuchEntityException(path));
                case 5 -> entityManager.perform(path[3], path[4], nestedFormParameters)
                                       .map(entity -> new TypedResponse.Single<>(200, entity))
                                       .orElseThrow(() -> noSuchEntityException(path));
                default -> throw new ResponseCodeException(400, "Unsupported path length for request");
            };
            case "DELETE" -> switch (path.length) {
                case 4 -> entityManager.delete(path[3])
                                       .map(resource -> new TypedResponse.Single<>(200, resource))
                                       .orElseThrow(() -> noSuchEntityException(path));
                default -> throw new ResponseCodeException(400, "Unsupported path length");
            };
            default -> throw new ResponseCodeException(405, "Unsupported operation for request");
        };
    }

    private ResponseCodeException noSuchEntityException(String[] path) {
        return ResponseCodeException.noSuchEntity(404, path[2].substring(0, path[2].length() - 1), path[3]);
    }
}
