package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;

import java.time.Clock;
import java.util.List;
import java.util.Map;

class RefundManager extends AbstractEntityManager<Refund> {
    private final StripeEntities stripeEntities;

    protected RefundManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Refund.class, "re", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Refund initialize(Refund refund, Map<String, Object> formData) throws ResponseCodeException {
        refund.setStatus("succeeded");
        if (refund.getAmount() == null && refund.getPaymentIntent() != null) {
            refund.setAmount(stripeEntities.getEntityManager(PaymentIntent.class)
                                           .get(refund.getPaymentIntent())
                                           .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "payment_intent", refund.getPaymentIntent()))
                                           .getAmount());
        }
        return refund;
    }

    @Override
    protected void validate(Refund refund) throws ResponseCodeException {
        super.validate(refund);
        String paymentIntentId = refund.getPaymentIntent();
        if (paymentIntentId == null && refund.getCharge() == null) {
            throw new ResponseCodeException(400, "One of the following params should be provided for this request: payment_intent or charge.");
        }
        // todo: support charges too
        PaymentIntent paymentIntent = stripeEntities.getEntityManager(PaymentIntent.class)
                                                    .get(paymentIntentId)
                                                    .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "payment_intent", paymentIntentId));
        if (!paymentIntent.getStatus()
                          .equals("succeeded")) {
            throw new ResponseCodeException(400, String.format("This PaymentIntent (%s) does not have a successful charge to refund.", paymentIntentId));
        }
    }

    @Override
    protected Refund perform(Refund existingRefund, Refund updatedRefund, String operation, Map<String, Object> formData) throws ResponseCodeException {
        if (operation.equals("cancel")) {
            String status = updatedRefund.getStatus();
            if (status.equals("pending")) {
                updatedRefund.setStatus("canceled");
            } else if (status.equals("canceled")) {
                // Already canceled, nothing to be done
            } else {
                throw new ResponseCodeException(400, "Can't cancel a refund that is in status: " + status);
            }
            return updatedRefund;
        }
        return super.perform(existingRefund, updatedRefund, operation, formData);
    }

    @Override
    public boolean canPerformOperation(String operation) {
        return operation.equals("cancel");
    }

    @Override
    public List<Refund> list(QueryParameters query) {
        return entities.values()
                       .stream()
                       .filter(filter(query, "payment_intent", Refund::getPaymentIntent))
                       .toList();
    }
}
