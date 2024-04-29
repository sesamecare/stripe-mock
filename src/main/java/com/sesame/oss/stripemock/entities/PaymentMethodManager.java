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

class PaymentMethodManager extends AbstractEntityManager<PaymentMethod> {
    // todo: test methods for things like charge_declined etc

    private final StripeEntities stripeEntities;

    PaymentMethodManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, PaymentMethod.class, "pm", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public void bootstrap() {
        try {
            // todo: more
            bootstrapTestCard("tok_bypassPending", "pm_card_bypassPending");
            bootstrapTestCard("tok_bypassPending", "tok_bypassPending");
            bootstrapTestCard("tok_chargeCustomerFail", "pm_card_chargeCustomerFail");
            bootstrapTestCard("tok_chargeCustomerFail", "tok_chargeCustomerFail");
            bootstrapTestCard("tok_visa_chargeDeclined", "pm_card_visa_chargeDeclined");
            bootstrapTestCard("tok_visa_chargeDeclined", "tok_visa_chargeDeclined");
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
        add(formData, null);
    }

    @Override
    protected PaymentMethod initialize(PaymentMethod paymentMethod, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
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
                } else if ("tok_bypassPending".equals(token)) {
                    // The US charge succeeds. Funds are added directly to your available balance, bypassing your pending balance.
                    // https://docs.stripe.com/testing?testing-method=tokens#available-balance
                    card.setBrand("visa");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("0077");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else if ("tok_visa_chargeDeclined".equals(token)) {
                    // todo: disallow this from being attached to a customer
                    card.setBrand("visa");
                    card.setCountry("US");
                    card.setFunding("credit");
                    card.setLast4("0002");
                    card.setExpMonth((long) today.getMonthValue());
                    card.setExpYear((long) (today.getYear() + 1));
                } else {
                    // todo: Support more payment methods later
                    throw new ResponseCodeException(400, "Unknown payment token: " + token);
                }
            }
        }

        return super.initialize(paymentMethod, formData, stripeAccount);
    }

    @Override
    protected void validate(PaymentMethod paymentMethod) throws ResponseCodeException {
        String customerId = paymentMethod.getCustomer();
        if (customerId != null) {
            stripeEntities.getEntityManager(Customer.class)
                          .get(customerId, null)
                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "customer", customerId));
        }
    }

    @Override
    protected PaymentMethod perform(PaymentMethod existingPaymentMethod, PaymentMethod updatedPaymentMethod, String operation, Map<String, Object> formData)
            throws ResponseCodeException {
        if ("attach".equals(operation)) {
            updatedPaymentMethod.setCustomer(updatedPaymentMethod.getCustomer());
            return updatedPaymentMethod;
        } else if ("detach".equals(operation)) {
            updatedPaymentMethod.setCustomer(null);
            return updatedPaymentMethod;
        }
        return super.perform(existingPaymentMethod, updatedPaymentMethod, operation, formData);
    }

    @Override
    public boolean canPerformOperation(String operation) {
        return operation.equals("attach") || operation.equals("detach");
    }

    @Override
    public List<PaymentMethod> list(QueryParameters query, String stripeAccount) {

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
                if (last4.equals("0341") || last4.equals("0002")) {
                    throw new ResponseCodeException(402, "Your card was declined.", "card_declined", null, "generic_decline", null);
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
