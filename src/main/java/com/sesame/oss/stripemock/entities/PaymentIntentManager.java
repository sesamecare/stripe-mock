package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

class PaymentIntentManager extends AbstractEntityManager<PaymentIntent> {
    private final StripeEntities stripeEntities;

    PaymentIntentManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, PaymentIntent.class, "pi");
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected PaymentIntent initialize(PaymentIntent paymentIntent, Map<String, Object> formData) throws ResponseCodeException {
        paymentIntent.setClientSecret(paymentIntent.getId() + "_secret_" + Utilities.randomStringOfLength(25));
        paymentIntent.setStatus("requires_payment_method");
        if (paymentIntent.getCaptureMethod() == null) {
            paymentIntent.setCaptureMethod("automatic");
        }
        if (paymentIntent.getConfirmationMethod() == null) {
            paymentIntent.setConfirmationMethod("automatic");
        }
        if (paymentIntent.getPaymentMethodOptions() == null) {
            PaymentIntent.PaymentMethodOptions paymentMethodOptions = new PaymentIntent.PaymentMethodOptions();
            paymentIntent.setPaymentMethodOptions(paymentMethodOptions);
            PaymentIntent.PaymentMethodOptions.Card card = new PaymentIntent.PaymentMethodOptions.Card();
            card.setRequestThreeDSecure("automatic");
            paymentMethodOptions.setCard(card);
        }
        if (paymentIntent.getPaymentMethodTypes() == null) {
            paymentIntent.setPaymentMethodTypes(new ArrayList<>());
        }
        if (paymentIntent.getPaymentMethodTypes()
                         .isEmpty()) {
            paymentIntent.getPaymentMethodTypes()
                         .add("card");
        }
        return paymentIntent;
    }

    @Override
    protected void validate(PaymentIntent paymentIntent) throws ResponseCodeException {
        super.validate(paymentIntent);
        String customer = paymentIntent.getCustomer();
        if (customer != null) {
            stripeEntities.getEntityManager(Customer.class)
                          .get(customer)
                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "customer", customer));
        }
        String paymentMethodId = paymentIntent.getPaymentMethod();
        if (paymentMethodId != null) {
            // Make sure the customer has it, or that it's a test card
            // it's ok if it's either one of the test tokens, or a payment method that belongs to the customer
            getPaymentMethodForCustomerOrThrow(paymentMethodId, customer);
        }
        if (paymentIntent.getAmount() == null) {
            throw new ResponseCodeException(400, "Must provide 'amount'");
        }
        if (paymentIntent.getCurrency() == null) {
            throw new ResponseCodeException(400, "Missing required param: currency.");
        }
    }

    /**
     * Fetches a payment method that is attached to the customer, or one of the test payment methods that aren't attached to anybody,
     * and can be used by anyone.
     */
    private PaymentMethod getPaymentMethodForCustomerOrThrow(String paymentMethod, String customer) throws ResponseCodeException {
        return stripeEntities.getEntityManager(PaymentMethod.class)
                             .get(paymentMethod)
                             .filter(pm -> pm.getCustomer() == null || Objects.equals(pm.getCustomer(), customer))
                             .orElseThrow(() -> new ResponseCodeException(400,
                                                                          String.format(
                                                                                  "No such PaymentMethod: '%s'; It's possible this PaymentMethod exists on one of your connected accounts, in which case you should retry this request on that connected account. Learn more at https://stripe.com/docs/connect/authentication",
                                                                                  paymentMethod)));
    }

    @Override
    protected PaymentIntent perform(PaymentIntent existingPaymentIntent, PaymentIntent updatedPaymentIntent, String operation, Map<String, Object> formData)
            throws ResponseCodeException {
        updatedPaymentIntent.setLastPaymentError(null);
        // We should make sure that we don't perform any changes to updatedPaymentIntent until we are sure that they will all succeed.
        // The code we have here today is a bit sloppy, and doesn't use transactions etc, but it's only a test mock, and not
        // actually a real payment platform
        String updatedPaymentIntentStatus = updatedPaymentIntent.getStatus();
        return switch (operation) {
            case "confirm" -> {
                try {
                    // todo: do pre-check state transitions as well, just like for the __update branch
                    String paymentMethodId = null;
                    if (updatedPaymentIntent.getPaymentMethod() == null) {
                        if (updatedPaymentIntent.getCustomer() != null) {
                            // todo: test that we throw if the customer is wrong
                            String id = updatedPaymentIntent.getCustomer();
                            Customer customer = stripeEntities.getEntityManager(Customer.class)
                                                              .get(id)
                                                              .orElseThrow(() -> ResponseCodeException.noSuchEntity(400,
                                                                                                                    "customer",
                                                                                                                    updatedPaymentIntent.getCustomer()));
                            Customer.InvoiceSettings invoiceSettings = customer.getInvoiceSettings();
                            if (invoiceSettings != null && invoiceSettings.getDefaultPaymentMethod() != null) {
                                paymentMethodId = invoiceSettings.getDefaultPaymentMethod();
                            } else if (customer.getDefaultSource() != null) {
                                // todo: tests that use default source to pay
                                paymentMethodId = customer.getDefaultSource();
                            }
                        }
                    } else {
                        paymentMethodId = updatedPaymentIntent.getPaymentMethod();
                    }
                    if (paymentMethodId == null) {
                        throw new ResponseCodeException(400,
                                                        "You cannot confirm this PaymentIntent because it's missing a payment method. You can either update the PaymentIntent with a payment method and then confirm it again, or confirm it again directly with a payment method.");
                    }
                    PaymentMethodManager.throwIfPaymentMethodIsNotValid(getPaymentMethodForCustomerOrThrow(paymentMethodId,
                                                                                                           updatedPaymentIntent.getCustomer()));
                    // In reality this would progress to "processing" first, and then to "succeeded" when it was actually successful,
                    // we're not going to bother with that here, since it will be immediately successful or failed
                    updatedPaymentIntent.setStatus("succeeded");
                    updatedPaymentIntent.setAmountReceived(updatedPaymentIntent.getAmount());
                    if (updatedPaymentIntent.getInvoice() != null) {
                        String invoiceId = updatedPaymentIntent.getInvoice();
                        Invoice invoice = stripeEntities.getEntityManager(Invoice.class)
                                                        .get(invoiceId)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "invoice", invoiceId));
                        invoice.setStatus("paid");
                        if (invoice.getSubscription() != null) {
                            String subscriptionId = invoice.getSubscription();
                            stripeEntities.getEntityManager(Subscription.class)
                                          .get(subscriptionId)
                                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "subscription", subscriptionId))
                                          .setStatus("active");
                        }
                    }
                } catch (ResponseCodeException e) {
                    StripeError lastPaymentError = new StripeError();
                    lastPaymentError.setCode(e.getCode());
                    lastPaymentError.setDeclineCode(e.getDeclineCode());
                    lastPaymentError.setType(e.getErrorType());
                    lastPaymentError.setMessage(e.getMessage());
                    // We have to set this on the *existing* payment intent, as the *updated* payment intent is discarded when we throw this exception
                    existingPaymentIntent.setLastPaymentError(lastPaymentError);
                    throw e;
                }
                yield updatedPaymentIntent;
            }
            case "cancel" -> {
                // todo: sanity checks
                if ("succeeded".equals(updatedPaymentIntentStatus)) {
                    throw new ResponseCodeException(400,
                                                    "You cannot cancel this PaymentIntent because it has a status of succeeded. Only a PaymentIntent with one of the following statuses may be canceled: requires_payment_method, requires_capture, requires_confirmation, requires_action, processing.");
                }
                updatedPaymentIntent.setStatus("canceled");
                yield updatedPaymentIntent;
            }
            case "apply_customer_balance" -> {
                // We know this is a valid operation, but as we don't use it, it's not supported yet
                throw new ResponseCodeException(400, "apply_customer_balance not supported yet");
            }
            case MAGIC_UPDATE_OPERATION -> {
                // https://stripe.com/docs/payments/paymentintents/lifecycle#intent-statuses
                if (updatedPaymentIntentStatus.equals("requires_payment_method") && updatedPaymentIntent.getPaymentMethod() != null) {
                    // todo: check for 3d secure and possibly move this to requires_action instead
                    updatedPaymentIntent.setStatus("requires_confirmation");
                }
                if (existingPaymentIntent.getCustomer() == null && updatedPaymentIntent.getCustomer() != null) {
                    // This object should always be expanded, so we may as well set it here.
                    // todo: alternatively consider a default set of expand paths for each object type
                    String id = updatedPaymentIntent.getCustomer();
                    updatedPaymentIntent.setCustomerObject(stripeEntities.getEntityManager(Customer.class)
                                                                         .get(id)
                                                                         .orElseThrow(() -> ResponseCodeException.noSuchEntity(400,
                                                                                                                               "customer",
                                                                                                                               updatedPaymentIntent.getCustomer())));
                }
                yield updatedPaymentIntent;
            }
            default -> super.perform(existingPaymentIntent, updatedPaymentIntent, operation, formData);
        };
    }
}
