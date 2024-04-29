package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class RefundManager extends AbstractEntityManager<Refund> {
    private final StripeEntities stripeEntities;

    protected RefundManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Refund.class, "re", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Refund initialize(Refund refund, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        refund.setStatus("succeeded");
        if (refund.getPaymentIntent() != null) {
            PaymentIntent paymentIntent = stripeEntities.getEntityManager(PaymentIntent.class)
                                                        .get(refund.getPaymentIntent(), stripeAccount)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400,
                                                                                                              "payment_intent",
                                                                                                              refund.getPaymentIntent()));
            // todo: this should get the charge and set refunded=true on the charge
            if (refund.getAmount() == null) {
                refund.setAmount(paymentIntent.getAmount());
            }
        }
        if (refund.getCharge() != null) {
            Charge charge = stripeEntities.getEntityManager(Charge.class)
                                          .get(refund.getCharge(), stripeAccount)
                                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "charge", refund.getCharge()));

            if (refund.getAmount() == null) {
                refund.setAmount(charge.getAmount());
                charge.setRefunded(true);
            } else {
                // Only fully refunded charges are marked as refunded
                charge.setRefunded(Objects.equals(refund.getAmount(), charge.getAmount()));
            }
        }
        // By registering this, it can be converted on the fly when expanded or fetched.
        BalanceTransactionManager balanceTransactionEntityManager = (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
        balanceTransactionEntityManager.register(refund.getBalanceTransaction(), refund);
        return refund;
    }

    @Override
    protected void validate(Refund refund) throws ResponseCodeException {
        super.validate(refund);
        String paymentIntentId = refund.getPaymentIntent();
        String chargeId = refund.getCharge();
        if (paymentIntentId != null) {
            PaymentIntent paymentIntent = stripeEntities.getEntityManager(PaymentIntent.class)
                                                        .get(paymentIntentId, null)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "payment_intent", paymentIntentId));
            if (!paymentIntent.getStatus()
                              .equals("succeeded")) {
                throw new ResponseCodeException(400, String.format("This PaymentIntent (%s) does not have a successful charge to refund.", paymentIntentId));
            }
        } else if (chargeId != null) {
            Charge charge = stripeEntities.getEntityManager(Charge.class)
                                          .get(chargeId, null)
                                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "charge", chargeId));
            if (!charge.getStatus()
                       .equals("succeeded")) {
                throw new ResponseCodeException(400, String.format("This Charge (%s) does not have a successful charge to refund.", chargeId));
            }
        } else {
            throw new ResponseCodeException(400, "One of the following params should be provided for this request: payment_intent or charge.");
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
    public List<Refund> list(QueryParameters query, String stripeAccount) {
        return entities.values()
                       .stream()
                       .filter(filter(query, "payment_intent", Refund::getPaymentIntent))
                       .toList();
    }
}
