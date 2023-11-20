package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

class CustomerManager extends AbstractEntityManager<Customer> {
    private final StripeEntities stripeEntities;

    protected CustomerManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Customer.class, "cus", 14);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Customer initialize(Customer customer, Map<String, Object> formData) throws ResponseCodeException {
        setDefaultSourceIfNecessary(customer, formData);
        customer.setDelinquent(false);
        customer.setInvoicePrefix(Utilities.randomStringOfLength(8)
                                           .toUpperCase());
        customer.setNextInvoiceSequence(1L);
        customer.setTaxExempt("none");
        return super.initialize(customer, formData);
    }

    @Override
    protected void validate(Customer customer) throws ResponseCodeException {
        super.validate(customer);
        Customer.InvoiceSettings invoiceSettings = customer.getInvoiceSettings();
        if (invoiceSettings != null && invoiceSettings.getDefaultPaymentMethod() != null) {
            stripeEntities.getEntityManager(PaymentMethod.class)
                          .get(invoiceSettings.getDefaultPaymentMethod())
                          .filter(pm -> pm.getCustomer() != null &&
                                        pm.getCustomer()
                                          .equals(customer.getId()))
                          .orElseThrow(() -> {
                              String entityId = invoiceSettings.getDefaultPaymentMethod();
                              return new ResponseCodeException(400,
                                                               String.format(
                                                                       "No such PaymentMethod: '%s'; It's possible this PaymentMethod exists on one of your connected accounts, in which case you should retry this request on that connected account. Learn more at https://stripe.com/docs/connect/authentication",
                                                                       entityId),
                                                               "resource_missing",
                                                               "invalid_request_error",
                                                               null);
                          });
        }

    }

    @Override
    protected Customer perform(Customer existingCustomer, Customer updatedCustomer, String operation, Map<String, Object> formData)
            throws ResponseCodeException {
        if (formData.containsKey("default_source") && formData.get("default_source") == null) {
            // We tried to unset this, but it's not allowed
            throw new ResponseCodeException(400,
                                            "You passed an empty string for 'default_source'. We assume empty values are an attempt to unset a parameter; however 'default_source' cannot be unset. You should remove 'default_source' from your request or supply a non-empty value.",
                                            "parameter_invalid_empty",
                                            "invalid_request_error",
                                            null);
        }

        if (operation.equals(MAGIC_UPDATE_OPERATION)) {
            setDefaultSourceIfNecessary(updatedCustomer, formData);
        }
        return updatedCustomer;
    }

    private static void setDefaultSourceIfNecessary(Customer updatedEntity, Map<String, Object> formData) {
        Object source = formData.get("source");
        if (source instanceof String defaultSource) {
            // This is actually a null check AND a cast.
            updatedEntity.setDefaultSource(defaultSource);
        }
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
