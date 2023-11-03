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
    public T add(Map<String, Object> formData) throws ResponseCodeException;

    public Optional<T> perform(String id, String operation, Map<String, Object> formData) throws ResponseCodeException;

    public Optional<T> update(String id, Map<String, Object> formData) throws ResponseCodeException;

    public Optional<T> get(String id) throws ResponseCodeException;

    public List<T> list(QueryParameters query);

    public Optional<T> delete(String id) throws ResponseCodeException;

    public void clear();

    public Class<T> getEntityClass();

    /**
     * Called each time {@link StripeMock#reset()} is called
     */
    public void bootstrap();
}
