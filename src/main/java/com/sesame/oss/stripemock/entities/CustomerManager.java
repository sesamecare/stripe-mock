package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Customer;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

class CustomerManager extends AbstractEntityManager<Customer> {
    protected CustomerManager(Clock clock) {
        super(clock, Customer.class, "cus");
    }

    @Override
    protected Customer perform(Customer existingEntity, Customer updatedEntity, String operation, Map<String, Object> formData) throws ResponseCodeException {
        if (operation.equals(MAGIC_UPDATE_OPERATION)) {
            Object source = formData.get("source");
            if (source instanceof String defaultSource) {
                // This is actually a null check AND a cast.
                updatedEntity.setDefaultSource(defaultSource);
            }
        }
        return updatedEntity;
    }

    @Override
    public Optional<Customer> delete(String id) {
        Customer customer = entities.get(id);
        if (customer == null) {
            return Optional.empty();
        }
        customer.setDeleted(true);
        return Optional.of(customer);
    }
}
