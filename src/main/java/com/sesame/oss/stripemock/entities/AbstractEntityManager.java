package com.sesame.oss.stripemock.entities;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.sesame.oss.stripemock.StripeMock;
import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.HasId;
import com.stripe.net.ApiResource;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

abstract class AbstractEntityManager<T extends ApiResource & HasId> implements EntityManager<T> {
    /**
     * This is a special operation that is used when an entity is updated.
     */
    protected static final String MAGIC_UPDATE_OPERATION = "__update";
    protected final Map<String, T> entities = new HashMap<>();
    protected final StripeEntities stripeEntities;
    protected final Clock clock;

    private final Class<T> entityClass;
    private final String idPrefix;
    private final int idLength;

    protected AbstractEntityManager(StripeEntities stripeEntities, Clock clock, Class<T> entityClass, String idPrefix, int idLength) {
        this.stripeEntities = stripeEntities;
        this.clock = clock;
        this.entityClass = entityClass;
        this.idPrefix = idPrefix;
        this.idLength = idLength;
    }

    @Override
    public T add(Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException(String.format("Entity %s does not support sub-entities. Attempted to add under parent %s/%s",
                                                              getNormalizedEntityName(),
                                                              parentEntityType,
                                                              parentEntityId));
    }

    @Override
    public T add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        // They give us form data, so this is a ghetto way to turn it back into an object.

        // We're the only ones that are allowed to specify what the id should be
        String id = Utilities.randomIdWithPrefix(idPrefix, idLength);
        // metadata must always be a map, even if it's empty. It should never be null.
        // So we can kill two birds with one stone, here
        Map<String, Object> metadata = (Map<String, Object>) formData.computeIfAbsent("metadata", ignored -> new HashMap<>());
        String idOverride = (String) metadata.remove(StripeMock.OVERRIDE_ID_FOR_TESTING);
        if (idOverride != null) {
            id = idOverride;
        }
        formData.put("id", id);
        formData.put("livemode", false);
        formData.put("created",
                     Instant.now(clock)
                            .getEpochSecond());
        ensureFormDataSpecifiesObjectType(formData);

        T entity = initialize(parse(formData), formData, stripeAccount);
        validate(entity);
        T existing = entities.putIfAbsent(id, entity);
        if (existing != null) {
            // This shouldn't happen unless people start overriding ids, but we should still check
            throw new ResponseCodeException(400, String.format("Overridden %s with id %s already exists", entityClass.getSimpleName(), id));
        }

        return entity;
    }

    @Override
    public final Optional<T> perform(String id, String operation, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        T existingEntity = entities.get(id);
        if (existingEntity == null) {
            return Optional.empty();
        }
        JsonObject root = Utilities.PRODUCER_GSON.toJsonTree(existingEntity)
                                                 .getAsJsonObject();
        merge(root, formData);
        T newEntity = ApiResource.GSON.fromJson(root, entityClass);
        T postOperationEntity = perform(existingEntity, newEntity, operation, formData);
        validate(postOperationEntity);
        entities.put(id, postOperationEntity);
        stripeEntities.updateLists(postOperationEntity);

        // For now, there's nothing to do here. In reality we'd do stuff like trigger webhooks etc.
        return Optional.of(postOperationEntity);
    }

    @Override
    public Optional<T> perform(String id, String operation, Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException(String.format("Entity %s does not support sub-entities. Attempted to perform operation %s under parent %s/%s",
                                                              getNormalizedEntityName(),
                                                              operation,
                                                              parentEntityType,
                                                              parentEntityId));
    }

    @Override
    public Optional<T> update(String id, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        return perform(id, MAGIC_UPDATE_OPERATION, formData, stripeAccount);
    }

    @Override
    public Optional<T> update(String id, Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException(String.format("Entity %s does not support sub-entities. Attempted to update under parent %s/%s",
                                                              getNormalizedEntityName(),
                                                              parentEntityType,
                                                              parentEntityId));
    }

    @Override
    public Optional<T> get(String id, String stripeAccount) throws ResponseCodeException {
        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public Optional<T> get(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException("Entity does not support sub-entities");
    }

    @Override
    public List<T> list(QueryParameters query, String stripeAccount) throws ResponseCodeException {
        return entities.values()
                       .stream()
                       .toList();
    }

    @Override
    public List<T> list(QueryParameters query, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException(String.format("Entity %s does not support sub-entities. Attempted to list under parent %s/%s",
                                                              getNormalizedEntityName(),
                                                              parentEntityType,
                                                              parentEntityId));
    }

    /**
     * This should be overridden by entities that support deletion, as it is not generically supported.
     * This default method always throws an exception unless overridden.
     */
    @Override
    public Optional<T> delete(String id) throws ResponseCodeException {
        // This shouldn't happen, as API classes that can't be deleted won't have a .delete() method on them.
        throw new ResponseCodeException(405, "Cannot delete");
    }

    @Override
    public Optional<T> delete(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        // Most entities do not support related sub-entities, so this is a reasonable default
        throw new UnsupportedOperationException(String.format("Entity %s does not support sub-entities. Attempted to delete under parent %s/%s",
                                                              getNormalizedEntityName(),
                                                              parentEntityType,
                                                              parentEntityId));
    }

    @Override
    public void clear() {
        entities.clear();
    }

    @Override
    public Class<T> getEntityClass() {
        return entityClass;
    }

    @Override
    public String getNormalizedEntityName() {
        // PaymentIntent -> payment_intent + s -> payment_intents
        return Utilities.snakeCase(entityClass.getSimpleName()) + "s";
    }

    @Override
    public void bootstrap() {

    }

    @Override
    public boolean canPerformOperation(String operation) {
        // The default is that we don't support any operations other than MAGIC_UPDATE_OPERATION.
        // This can be seen in the default implementation of perform().
        // As such, it's reasonable to return false as a default here, and let implementations override it as they choose.
        return false;
    }

    /**
     * This is called after the entity is created, but before it is stored in memory.
     * This means that the entity can be augmented, but also rejected if the state is inconsistent.
     * By default, this method does nothing.
     *
     * @param entity        the new entity
     * @param formData      the data used to create the entity
     * @param stripeAccount the stripe connect account for the request
     * @implNote his method does <b>not</b> exist on the {@link EntityManager} interface, as it is an implementation detail, and should never be called from the outside.
     */
    protected T initialize(T entity, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        return entity;
    }

    /**
     * This is called after the entity is created or updated, but before it is stored in memory.
     * This should not mutate the entity. For that, use the {@link #MAGIC_UPDATE_OPERATION} in the {@link #perform(ApiResource, ApiResource, String, Map)} method.
     *
     * @param entity the new entity
     * @implNote his method does <b>not</b> exist on the {@link EntityManager} interface, as it is an implementation detail, and should never be called from the outside.
     */
    protected void validate(T entity) throws ResponseCodeException {
        if (entity.getId() == null) {
            throw new IllegalStateException("Entity is missing id: " + entity);
        }
    }

    /**
     * @param existingEntity the existing entity before we have applied any changes to it
     * @param updatedEntity  the updated entity after we applied any changes that might have been included in the form data to it
     * @param operation      the operation we were asked to perform
     * @param formData
     * @implNote his method does <b>not</b> exist on the {@link EntityManager} interface, as it is an implementation detail, and should never be called from the outside.
     */
    protected T perform(T existingEntity, T updatedEntity, String operation, Map<String, Object> formData) throws ResponseCodeException {
        if (MAGIC_UPDATE_OPERATION.equals(operation)) {
            return updatedEntity;
        }
        // As this mock gets more and more complete, this should happen less and less often.
        throw new ResponseCodeException(400,
                                        String.format("Entity type %s does not yet support operation '%s'",
                                                      Utilities.snakeCase(entityClass.getSimpleName()),
                                                      operation));
    }

    private void ensureFormDataSpecifiesObjectType(Map<String, Object> formData) {
        if (!formData.containsKey("object")) {
            formData.put("object", Utilities.snakeCase(entityClass.getSimpleName()));
        }
    }

    public void merge(JsonObject parent, Map<String, Object> formData) {
        for (Map.Entry<String, Object> entry : formData.entrySet()) {
            String name = entry.getKey();
            if (name.equals("expand")) {
                // Right now this seems to be the only Object[] that's actually used, and this will never he part of the actual entity, so we can skip this.
                // It's possible that we could do this in a more elegant way, so the AbstractEntityManager doesn't need to know about 'expand', but we can
                // cross that bridge later.
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Map m) {
                JsonObject child = parent.getAsJsonObject(name);
                if (child == null) {
                    // This doesn't exist in the existing object, to we need to create it.
                    child = new JsonObject();
                    parent.add(name, child);
                }
                merge(child, m);
            } else {
                switch (value) {
                    case null -> parent.add(name, JsonNull.INSTANCE);
                    case Number n -> parent.addProperty(name, n);
                    case String s -> parent.addProperty(name, s);
                    case Boolean b -> parent.addProperty(name, b);
                    // This would happen with arrays, but I don't think there are any arrays in stripe's api
                    default -> throw new IllegalArgumentException("Unsupported data type: " + value);
                }
            }
        }
    }

    protected T parse(Map<String, Object> formData) {
        String mapAsJson = Utilities.PRODUCER_GSON.toJson(formData);
        return ApiResource.GSON.fromJson(mapAsJson, entityClass);
    }

    protected Predicate<T> filter(QueryParameters query, String field, Function<T, String> getter) {
        return query.getFirst(field)
                    .<Predicate<T>>map(filterValue -> entity -> Objects.equals(filterValue, getter.apply(entity)))
                    .orElseGet(() -> entity -> true);
    }
}
