package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.HasId;
import com.stripe.net.ApiResource;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class StripeEntities {
    private final Map<Class<? extends ApiResource>, EntityManager<? extends ApiResource>> entityManagers = new HashMap<>();
    private final Map<String, EntityManager<? extends ApiResource>> entityManagersByNormalizedEntityName = new HashMap<>();

    public StripeEntities(Clock clock) {
        // As these entity managers will need to have access to each other, often in a circular dependency fashion,
        // we're passing in the StripeEntities so they can do resolution using it. This means that we're leaking 'this'
        // before the object is constructed, but it still seems more elegant than calling a setter on each manager later.

        add(new TransferReversalManager(clock, this));
        add(new PaymentMethodManager(clock, this));
        add(new PaymentIntentManager(clock, this));
        add(new SubscriptionManager(clock, this));
        add(new RefundManager(clock, this));
        add(new SetupIntentManager(clock));
        add(new TransferManager(clock, this));
        add(new CustomerManager(clock, this));
        add(new InvoiceManager(clock, this));
        add(new InvoiceItemManager(clock, this));
        add(new ProductManager(clock));
        add(new AccountManager(clock));
    }

    private void add(EntityManager<?> entityManager) {
        entityManagers.put(entityManager.getEntityClass(), entityManager);
        entityManagersByNormalizedEntityName.put(entityManager.getNormalizedEntityName(), entityManager);
        entityManager.bootstrap();
    }

    public <T extends ApiResource & HasId> EntityManager<T> getEntityManager(Class<T> entityClass) {
        return (EntityManager<T>) entityManagers.get(entityClass);
    }

    /**
     * @param normalizedEntityName {@code payment_intents} for {@link com.stripe.model.PaymentIntent} for example. It's whatever the URL path would be start with.
     * @see EntityManager#getNormalizedEntityName()
     */
    public EntityManager<?> getEntityManager(String normalizedEntityName) {
        EntityManager<? extends ApiResource> entityManager = entityManagersByNormalizedEntityName.get(normalizedEntityName);
        if (entityManager == null) {
            throw new IllegalStateException("Unable to find entity manager for normalized entity name: " + normalizedEntityName);
        }
        return entityManager;
    }

    public void clear() {
        for (EntityManager<?> entityManager : entityManagers.values()) {
            entityManager.clear();
            entityManager.bootstrap();
        }
    }

    public Optional<?> getEntityById(String id) {
        // This isn't very fast, and we could be more selective by using the prefix in the id, but it'll do for now.
        // Also, there likely won't be a lot of entities in memory in any given unit test
        return entityManagers.values()
                             .stream()
                             .map(entityManager -> safeGet(id, entityManager))
                             .filter(Objects::nonNull)
                             .findAny();
    }

    private static Object safeGet(String id, EntityManager<?> entityManager) {
        try {
            return entityManager.get(id)
                                .orElse(null);
        } catch (ResponseCodeException e) {
            // We have to do this, rather than just rely on the optional, as each entity is able to throw a custom exception.
            // See for example the AccountManager, which has special treatment.
            return null;
        }
    }
}
