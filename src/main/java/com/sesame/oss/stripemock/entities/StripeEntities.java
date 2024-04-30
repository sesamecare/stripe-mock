package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.HasId;
import com.stripe.model.StripeCollection;
import com.stripe.net.ApiResource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.*;

public class StripeEntities {
    private final Map<Class<? extends ApiResource>, EntityManager<? extends ApiResource>> entityManagers = new HashMap<>();
    private final Map<String, EntityManager<? extends ApiResource>> entityManagersByNormalizedEntityName = new HashMap<>();
    private final Map<String, List<ParentCollection>> childToParentCollectionMappings = new HashMap<>();

    public StripeEntities(Clock clock) {
        // As these entity managers will need to have access to each other, often in a circular dependency fashion,
        // we're passing in the StripeEntities so they can do resolution using it. This means that we're leaking 'this'
        // before the object is constructed, but it still seems more elegant than calling a setter on each manager later.

        add(new TransferReversalManager(clock, this));
        add(new PaymentMethodManager(clock, this));
        add(new PaymentIntentManager(clock, this));
        add(new SubscriptionManager(clock, this));
        add(new RefundManager(clock, this));
        add(new SetupIntentManager(clock, this));
        add(new TransferManager(clock, this));
        add(new CustomerManager(clock, this));
        add(new InvoiceManager(clock, this));
        add(new InvoiceItemManager(clock, this));
        add(new ChargeManager(clock, this));
        add(new PayoutManager(clock, this));
        add(new BalanceTransactionManager(clock, this));
        add(new BankAccountManager(clock, this));
        add(new ProductManager(clock, this));
        add(new AccountManager(clock, this));
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
        childToParentCollectionMappings.clear();
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

    void bindChildToParentCollection(Class<?> parentEntityType, String parentEntityId, String collectionGetterName, String childEntityId) {
        childToParentCollectionMappings.computeIfAbsent(childEntityId, k -> new ArrayList<>())
                                       .add(new ParentCollection(parentEntityType, parentEntityId, collectionGetterName));
    }

    void unbindChildFromParentCollection(Class<?> parentEntityType, String parentEntityId, String collectionGetterName, String childEntityId) {
        childToParentCollectionMappings.computeIfAbsent(childEntityId, k -> new ArrayList<>())
                                       .remove(new ParentCollection(parentEntityType, parentEntityId, collectionGetterName));
    }

    /**
     * This somewhat ugly hack solves the issue that entity references are not permanent. Any update to an entity recreates that entity from scratch.
     * This means that any collections etc that held that entity will now hold an old object. We need to make sure that children in collections held
     * by parents are updated.
     *
     * @param childEntity the child that was just updated
     * @param <P>         the type of the parent
     * @param <C>         the type of the child
     */
    <P extends ApiResource & HasId, C extends HasId> void updateLists(C childEntity) {
        List<ParentCollection> parentCollectionsThatReferenceTheChild = childToParentCollectionMappings.get(childEntity.getId());
        if (parentCollectionsThatReferenceTheChild == null) {
            return;
        }
        try {
            for (ParentCollection parentCollectionThatReferencesTheChild : parentCollectionsThatReferenceTheChild) {
                Class<P> parentType = (Class<P>) parentCollectionThatReferencesTheChild.parentEntityType();
                EntityManager<P> parentEntityManager = getEntityManager(parentType);
                P parent = parentEntityManager.get(parentCollectionThatReferencesTheChild.parentEntityId(), null)
                                              .orElseThrow();
                // It would have been nice to use something like Charge::getRefunds here, but it was too hard to get it to work
                // with the types, since it would be in a collection anyway. Maybe there's a way to make it work, but for now,
                // strings are going to have to do.
                // Here I am, painting myself into a smaller and smaller corner with my design decisions =)
                Method method = parentCollectionThatReferencesTheChild.parentEntityType()
                                                                      .getMethod(parentCollectionThatReferencesTheChild.collectionGetterName());
                StripeCollection<C> collection = (StripeCollection<C>) method.invoke(parent);
                List<C> list = collection.getData();
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i)
                            .getId()
                            .equals(childEntity.getId())) {
                        list.set(i, childEntity);
                    }
                }
            }
        } catch (ResponseCodeException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            // This shouldn't happen
            throw new AssertionError(e);
        }
    }

    private static Object safeGet(String id, EntityManager<?> entityManager) {
        try {
            return entityManager.get(id, null)
                                .orElse(null);
        } catch (ResponseCodeException e) {
            // We have to do this, rather than just rely on the optional, as each entity is able to throw a custom exception.
            // See for example the AccountManager, which has special treatment.
            return null;
        }
    }

    private record ParentCollection(Class<?> parentEntityType,
                                    String parentEntityId,
                                    String collectionGetterName) {}
}
