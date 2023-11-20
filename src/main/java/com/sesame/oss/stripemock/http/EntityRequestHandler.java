package com.sesame.oss.stripemock.http;

import com.sesame.oss.stripemock.entities.EntityManager;
import com.sesame.oss.stripemock.entities.StripeEntities;

import java.util.Map;

class EntityRequestHandler {
    private final StripeEntities stripeEntities;

    EntityRequestHandler(StripeEntities stripeEntities) {
        this.stripeEntities = stripeEntities;
    }

    public EntityResponse handleRequest(String method, String[] path, QueryParameters query, Map<String, Object> nestedFormParameters)
            throws ResponseCodeException {
        EntityManager<?> entityManager = stripeEntities.getEntityManager(path[2]);
        return switch (method) {
            case "GET" -> switch (path.length) {
                case 3 -> new EntityResponse.Multiple(200, entityManager.list(query));
                case 4 -> entityManager.get(path[3])
                                       .map(resource -> new EntityResponse.Single(200, resource))
                                       .orElseThrow(() -> noSuchEntityException(path));
                default -> throw new ResponseCodeException(400, "Unsupported path length");
            };
            case "POST" -> switch (path.length) {
                case 3 -> new EntityResponse.Single(200, entityManager.add(nestedFormParameters));
                case 4 -> entityManager.update(path[3], nestedFormParameters)
                                       .map(entity -> new EntityResponse.Single(200, entity))
                                       .orElseThrow(() -> noSuchEntityException(path));
                case 5 -> {
                    if (entityManager.canPerformOperation(path[4])) {
                        yield entityManager.perform(path[3], path[4], nestedFormParameters)
                                           .map(entity -> new EntityResponse.Single(200, entity))
                                           .orElseThrow(() -> noSuchEntityException(path));
                    } else {
                        EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                        yield new EntityResponse.Single(200, subEntityManager.add(nestedFormParameters, path[2], path[3]));
                    }
                }
                default -> throw new ResponseCodeException(400, "Unsupported path length for request");
            };
            case "DELETE" -> switch (path.length) {
                case 4 -> entityManager.delete(path[3])
                                       .map(resource -> new EntityResponse.Single(200, resource))
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
