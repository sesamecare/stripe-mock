package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.StripeMock;
import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PaymentMethodManager extends AbstractEntityManager<PaymentMethod> {
    // todo. also the tokens and payment methods per country: https://stripe.com/docs/testing?testing-method=payment-methods
    static final Set<String> TEST_PAYMENT_METHODS = Set.of("pm_card_visa",
                                                           "pm_card_visa_debit",
                                                           "pm_card_mastercard",
                                                           "pm_card_mastercard_debit",
                                                           "pm_card_mastercard_prepaid",
                                                           "pm_card_amex",
                                                           "pm_card_discover",
                                                           "pm_card_diners",
                                                           "pm_card_jcb",
                                                           "pm_card_unionpay");

    static final Set<String> TEST_PAYMENT_TOKENS = Set.of("tok_visa",
                                                          "tok_visa_debit",
                                                          "tok_mastercard",
                                                          "tok_mastercard_debit",
                                                          "tok_mastercard_prepaid",
                                                          "tok_amex",
                                                          "tok_discover",
                                                          "tok_diners",
                                                          "tok_jcb",
                                                          "tok_unionpay");

    // todo: test methods for things like charge_declined etc

    private final StripeEntities stripeEntities;

    PaymentMethodManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, PaymentMethod.class, "pm");
        this.stripeEntities = stripeEntities;
    }

    @Override
    public void bootstrap() {
        try {
            // todo: more
            bootstrapTestCard("tok_chargeCustomerFail", "pm_card_chargeCustomerFail");
            bootstrapTestCard("tok_chargeCustomerFail", "tok_chargeCustomerFail");
            bootstrapTestCard("tok_mastercard", "pm_card_mastercard");
            bootstrapTestCard("tok_mastercard", "tok_mastercard");
            bootstrapTestCard("tok_visa", "pm_card_visa");
            bootstrapTestCard("tok_visa", "tok_visa");
            bootstrapTestCard("tok_amex", "pm_card_amex");
            bootstrapTestCard("tok_amex", "tok_amex");

        } catch (ResponseCodeException e) {
            throw new AssertionError(e);
        }
    }

    private void bootstrapTestCard(String token, String id) throws ResponseCodeException {
        Map<String, Object> formData = new HashMap<>();
        formData.put("type", "card");
        formData.put("card", Map.of("token", token));
        Map<String, Object> metadata = new HashMap<>();
        formData.put("metadata", metadata);
        metadata.put(StripeMock.OVERRIDE_ID_FOR_TESTING, id);
        add(formData);
    }

    @Override
    protected PaymentMethod initialize(PaymentMethod paymentMethod, Map<String, Object> formData) throws ResponseCodeException {
        if ("card".equals(paymentMethod.getType())) {
            PaymentMethod.Card card = paymentMethod.getCard();

            if (formData.get("card") instanceof Map cardData) {
                Object token = cardData.get("token");
                LocalDate today = LocalDate.now(clock);
                if ("tok_mastercard".equals(token)) {
                    card.setBrand("mastercard");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("4444");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else if ("tok_amex".equals(token)) {
                    card.setBrand("amex");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("8431");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else if ("tok_visa".equals(token)) {
                    card.setBrand("visa");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("4242");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else if ("tok_chargeCustomerFail".equals(token)) {
                    // This can be attached to a customer but will fail when actually used
                    // https://stripe.com/docs/testing?testing-method=tokens#declined-payments
                    card.setBrand("visa");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("0341");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else {
                    // todo: Support more payment methods later
                    throw new ResponseCodeException(400, "Unknown payment token: " + token);
                }
            }
        }

        return super.initialize(paymentMethod, formData);
    }

    @Override
    protected PaymentMethod perform(PaymentMethod existingPaymentMethod, PaymentMethod updatedPaymentMethod, String operation, Map<String, Object> formData)
            throws ResponseCodeException {
        if ("attach".equals(operation)) {
            String customerId = updatedPaymentMethod.getCustomer();
            if (customerId == null) {
                // todo: align with stripe error message
                throw new ResponseCodeException(400, "Most provide a customer");
            }
            // todo: assert that we throw if the customer can't be found
            Customer customer = stripeEntities.getEntityManager(Customer.class)
                                              .get(customerId)
                                              .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "customer", customerId));
            Customer.InvoiceSettings invoiceSettings = customer.getInvoiceSettings();
            if (invoiceSettings == null) {
                invoiceSettings = new Customer.InvoiceSettings();
                customer.setInvoiceSettings(invoiceSettings);
            }
            if (invoiceSettings.getDefaultPaymentMethod() == null) {
                invoiceSettings.setDefaultPaymentMethod(updatedPaymentMethod.getId());
            }
            updatedPaymentMethod.setCustomer(customerId);
            return updatedPaymentMethod;
        } else if ("detach".equals(operation)) {
            updatedPaymentMethod.setCustomer(null);
            return updatedPaymentMethod;
        }
        return super.perform(existingPaymentMethod, updatedPaymentMethod, operation, formData);
    }

    @Override
    public List<PaymentMethod> list(QueryParameters query) {

        return entities.values()
                       .stream()
                       .filter(filter(query, "customer", PaymentMethod::getCustomer).and(filter(query, "type", PaymentMethod::getType)))
                       .toList();
    }

    public static void throwIfPaymentMethodIsNotValid(PaymentMethod paymentMethod) throws ResponseCodeException {
        // todo: match with stripe's error messages, including specific error messages for specific cards
        PaymentMethod.Card card = paymentMethod.getCard();
        if (card == null) {
            throw new ResponseCodeException(400, "No card");
        }
        // todo: test all this stuff
        String last4 = card.getLast4();
        switch (card.getBrand()) {
            case "mastercard" -> {
                if (!last4.equals("4444")) {
                    throw new ResponseCodeException(400, "Invalid card");
                }
            }
            case "visa" -> {
                if (last4.equals("0341")) {
                    throw new ResponseCodeException(402, "Your card was declined.", "card_declined", null, "generic_decline");
                }
                if (!last4.equals("4242")) {
                    throw new ResponseCodeException(400, "Invalid card");
                }

            }
            case "amex" -> {
                if (!last4.equals("8431")) {
                    throw new ResponseCodeException(400, "Invalid card");
                }

            }
            // We shouldn't end up here, as initialize() should check for us, but still
            default -> throw new ResponseCodeException(400, "Unsupported card brand: " + card.getBrand());
        }

        // todo. support things that are not cards
    }
}
