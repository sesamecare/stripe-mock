package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.StripeMock;
import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.HasId;
import com.stripe.net.ApiResource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface EntityManager<T extends ApiResource & HasId> {
    public T add(Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException;

    public T add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException;

    public Optional<T> perform(String id, String operation, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException;

    public Optional<T> perform(String id, String operation, Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException;

    public Optional<T> update(String id, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException;

    public Optional<T> update(String id, Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException;

    public Optional<T> get(String id, String stripeAccount) throws ResponseCodeException;

    public Optional<T> get(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException;

    public List<T> list(QueryParameters query, String stripeAccount) throws ResponseCodeException;

    public List<T> list(QueryParameters query, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException;

    public Optional<T> delete(String id) throws ResponseCodeException;

    public Optional<T> delete(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException;

    public void clear();

    public Class<T> getEntityClass();

    /**
     * For example {@code payment_intents} for {@link com.stripe.model.PaymentIntent}.
     */
    public String getNormalizedEntityName();

    /**
     * Called each time {@link StripeMock#reset()} is called
     */
    public void bootstrap();

    /**
     * This is used to distinguish whether the part after the entity id is an operation, like in {@code /v1/payment_intents/pi_abc123/confirm},
     * or a different entity, like in {@code /v1/transfers/tr_abc123/reversals}.
     *
     * @param operation the operation, such as "confirm" and "reversals" from the examples.
     * @return whether calling {@link #perform(String, String, Map, String)} with this operation is supported or not
     */
    public boolean canPerformOperation(String operation);
}
