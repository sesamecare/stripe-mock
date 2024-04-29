package com.sesame.oss.stripemock.http;

import com.sesame.oss.stripemock.entities.EntityManager;
import com.sesame.oss.stripemock.entities.StripeEntities;
import com.sesame.oss.stripemock.util.BalanceUtilities;
import com.stripe.model.BalanceTransaction;
import com.sun.net.httpserver.Headers;

import java.util.List;
import java.util.Map;

class EntityRequestHandler {
    private final StripeEntities stripeEntities;

    EntityRequestHandler(StripeEntities stripeEntities) {
        this.stripeEntities = stripeEntities;
    }

    public EntityResponse handleRequest(String method, String[] path, QueryParameters query, Headers requestHeaders, Map<String, Object> nestedFormParameters)
            throws ResponseCodeException {
        String stripeAccount = requestHeaders.getFirst("Stripe-Account");
        if ("balance".equals(path[2]) && "GET".equals(method)) {
            // This is a special case, as balance is not an entity.
            // If we have more things that are not entities in the future, we might have to do something more elegant,
            // but for now, it's just for balances
            List<BalanceTransaction> balanceTransactions = stripeEntities.getEntityManager(BalanceTransaction.class)
                                                                         .list(query, stripeAccount);
            return new EntityResponse.Single(200, BalanceUtilities.createBalance(balanceTransactions, stripeAccount));
        }
        EntityManager<?> entityManager = stripeEntities.getEntityManager(path[2]);
        return switch (method) {
            case "GET" -> switch (path.length) {
                case 3 -> new EntityResponse.Multiple(200, entityManager.list(query, stripeAccount));
                case 4 -> entityManager.get(path[3], stripeAccount)
                                       .map(resource -> new EntityResponse.Single(200, resource))
                                       .orElseThrow(() -> noSuchEntityException(path));
                case 5 -> {
                    EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                    yield new EntityResponse.Multiple(200, subEntityManager.list(query, stripeAccount, path[2], path[3]));
                }
                case 6 -> {
                    EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                    yield subEntityManager.get(path[5], stripeAccount, path[2], path[3])
                                          .map(resource -> new EntityResponse.Single(200, resource))
                                          .orElseThrow(() -> noSuchEntityException(path));
                }
                default -> throw new ResponseCodeException(400, "Unsupported path length");
            };
            case "POST" -> switch (path.length) {
                case 3 -> new EntityResponse.Single(200, entityManager.add(nestedFormParameters, stripeAccount));
                case 4 -> entityManager.update(path[3], nestedFormParameters, stripeAccount)
                                       .map(entity -> new EntityResponse.Single(200, entity))
                                       .orElseThrow(() -> noSuchEntityException(path));
                case 5 -> {
                    if (entityManager.canPerformOperation(path[4])) {
                        yield entityManager.perform(path[3], path[4], nestedFormParameters, stripeAccount)
                                           .map(entity -> new EntityResponse.Single(200, entity))
                                           .orElseThrow(() -> noSuchEntityException(path));
                    } else {
                        EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                        yield new EntityResponse.Single(200, subEntityManager.add(nestedFormParameters, stripeAccount, path[2], path[3]));
                    }
                }
                case 6 -> {
                    EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                    yield subEntityManager.update(path[5], nestedFormParameters, stripeAccount, path[2], path[3])
                                          .map(resource -> new EntityResponse.Single(200, resource))
                                          .orElseThrow(() -> noSuchEntityException(path));
                }
                default -> throw new ResponseCodeException(400, "Unsupported path length for request");
            };
            case "DELETE" -> switch (path.length) {
                case 4 -> entityManager.delete(path[3])
                                       .map(resource -> new EntityResponse.Single(200, resource))
                                       .orElseThrow(() -> noSuchEntityException(path));
                case 6 -> {
                    EntityManager<?> subEntityManager = stripeEntities.getEntityManager(path[4]);
                    yield subEntityManager.delete(path[5], stripeAccount, path[2], path[3])
                                          .map(resource -> new EntityResponse.Single(200, resource))
                                          .orElseThrow(() -> noSuchEntityException(path));
                }
                default -> throw new ResponseCodeException(400, "Unsupported path length");
            };
            default -> throw new ResponseCodeException(405, "Unsupported operation for request");
        };
    }

    private ResponseCodeException noSuchEntityException(String[] path) {
        return ResponseCodeException.noSuchEntity(404, path[2].substring(0, path[2].length() - 1), path[3]);
    }
}
