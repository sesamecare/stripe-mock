package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.StripeError;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.CustomerUpdateParams.InvoiceSettings;
import com.stripe.param.PaymentIntentUpdateParams.SetupFutureUsage;
import com.stripe.param.PaymentIntentUpdateParams.Shipping;
import com.stripe.param.PaymentIntentUpdateParams.Shipping.Address;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentIntentTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        PaymentIntentCreateParams input = PaymentIntentCreateParams.builder()
                                                                   .setAmount(10_000L)
                                                                   .setCurrency("USD")
                                                                   .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        PaymentIntent pi1 = PaymentIntent.create(input, options);
        PaymentIntent pi2 = PaymentIntent.create(input, options);
        assertEquals(pi1, pi2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        String idempotencyKey = String.valueOf(Math.random());
        PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                      .setAmount(10_00L)
                                                      .setCurrency("usd")
                                                      .build(),
                             RequestOptions.builder()
                                           .setIdempotencyKey(idempotencyKey)
                                           .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                                                     .setAmount(10_00L)
                                                                                                                     .setCurrency("eur")
                                                                                                                     .build(),
                                                                                            RequestOptions.builder()
                                                                                                          .setIdempotencyKey(idempotencyKey)
                                                                                                          .build()));
        assertEquals(String.format(
                             "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request.",
                             idempotencyKey),
                     idempotencyException.getStripeError()
                                         .getMessage());
    }

    // todo: test only either automatic payment methods or confirmation method:
    //  com.stripe.exception.InvalidRequestException: You may only specify one of these parameters: automatic_payment_methods, confirmation_method.
    //  com.stripe.exception.InvalidRequestException: You may only specify one of these parameters: ["automatic_payment_methods", "payment_method_types"].
    @Test
    void testPaymentIntent() throws Exception {
        PaymentIntent createdPaymentIntent = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .putMetadata("integration_test", "true")
                                                              .setTransferGroup("my_transfer_group")
                                                              .addPaymentMethodType("card")
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                                                              .setErrorOnRequiresAction(false)
                                                              .build());
        assertNotNull(createdPaymentIntent.getClientSecret());
        assertEquals(Collections.singletonList("card"), createdPaymentIntent.getPaymentMethodTypes());

        PaymentIntent retrievedPaymentIntent = PaymentIntent.retrieve(createdPaymentIntent.getId());
        assertNotNull(retrievedPaymentIntent.getClientSecret());
        assertEquals(createdPaymentIntent, retrievedPaymentIntent);

        StripeException missingCustomer = assertThrows(StripeException.class,
                                                       () -> retrievedPaymentIntent.update(PaymentIntentUpdateParams.builder()
                                                                                                                    .setCustomer("cus_abc123")
                                                                                                                    .build()));
        assertEquals("No such customer: 'cus_abc123'",
                     missingCustomer.getStripeError()
                                    .getMessage());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .build());
        PaymentIntent updatedPaymentIntent = //
                retrievedPaymentIntent.update(PaymentIntentUpdateParams.builder()
                                                                       .setCustomer(customer.getId())
                                                                       .setSetupFutureUsage(SetupFutureUsage.OFF_SESSION)
                                                                       .setShipping(Shipping.builder()
                                                                                            .setName("stripe-mock test")
                                                                                            .setAddress(Address.builder()
                                                                                                               .setCountry("US")
                                                                                                               .setCity("New York")
                                                                                                               .setLine1("1 Main Street")
                                                                                                               .setState("NY")
                                                                                                               .setPostalCode("12345")
                                                                                                               .build())
                                                                                            .build())
                                                                       .build());

        PaymentIntent retrievedUpdatedPaymentIntent = PaymentIntent.retrieve(createdPaymentIntent.getId());
        assertEquals(updatedPaymentIntent, retrievedUpdatedPaymentIntent);

        StripeException wrongPaymentMethod = assertThrows(StripeException.class,
                                                          () -> createdPaymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                                                                                       .setPaymentMethod(
                                                                                                                               "pm_card_visa_chargeDeclined")
                                                                                                                       .build()));
        assertEquals("Your card was declined.",
                     wrongPaymentMethod.getStripeError()
                                       .getMessage());
        PaymentIntent postConfirmationFailurePaymentIntent = PaymentIntent.retrieve(createdPaymentIntent.getId());
        StripeError lastPaymentError = postConfirmationFailurePaymentIntent.getLastPaymentError();
        assertNotNull(lastPaymentError);
        assertEquals("Your card was declined.", lastPaymentError.getMessage());

        PaymentIntent confirmedPaymentIntent = createdPaymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                                                                      .setPaymentMethod("pm_card_visa")
                                                                                                      .setCaptureMethod(PaymentIntentConfirmParams.CaptureMethod.AUTOMATIC)
                                                                                                      .build());
        // todo: assert that we only transition into this state if the capture method is automatic
        assertEquals("succeeded", confirmedPaymentIntent.getStatus());
    }

    /**
     * This means that the payment intent itself does not require a payment method, since it's attached to the customer already.
     */
    @Test
    void shouldNotAllowConfirmationWithoutExplicitPaymentMethodEvenIfCustomerHasSavedPaymentMethod() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Stripe-mock test")
                                                                .build());
        PaymentMethod paymentMethod = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .setType(PaymentMethodCreateParams.Type.CARD)
                                                                                    .setCard(PaymentMethodCreateParams.Token.builder()
                                                                                                                            .setToken("tok_mastercard")
                                                                                                                            .build())
                                                                                    .build())
                                                   .attach(PaymentMethodAttachParams.builder()
                                                                                    .setCustomer(customer.getId())
                                                                                    .build());
        customer.update(CustomerUpdateParams.builder()
                                            .setInvoiceSettings(InvoiceSettings.builder()
                                                                               .setDefaultPaymentMethod(paymentMethod.getId())
                                                                               .build())
                                            .build());
        PaymentIntent paymentIntent = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .setCustomer(customer.getId())
                                                              .putMetadata("integration_test", "true")
                                                              .addPaymentMethodType("card")
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                                                              .setErrorOnRequiresAction(false)
                                                              .build());
        InvalidRequestException missingPaymentMethod = assertThrows(InvalidRequestException.class, paymentIntent::confirm);
        assertEquals(String.format(
                             "You cannot confirm this PaymentIntent because it's missing a payment method. To confirm the PaymentIntent with %s, specify a payment method attached to this customer along with the customer ID.",
                             customer.getId()),
                     missingPaymentMethod.getStripeError()
                                         .getMessage());
        assertEquals("payment_intent_unexpected_state",
                     missingPaymentMethod.getStripeError()
                                         .getCode());

    }

    @Test
    void shouldRejectPaymentIntentForNonexistentCustomer() {
        StripeException stripeException = assertThrows(StripeException.class,
                                                       () -> PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                                           .setAmount(10_00L)
                                                                                                           .setCurrency("usd")
                                                                                                           .setCustomer("cus_nope")
                                                                                                           .build()));
        assertEquals("No such customer: 'cus_nope'",
                     stripeException.getStripeError()
                                    .getMessage());
    }

    @Test
    void shouldRejectPaymentIntentWithoutAmount() {
        StripeException stripeException = assertThrows(StripeException.class,
                                                       () -> PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                                           .setCurrency("usd")
                                                                                                           .build()));
        assertEquals("Missing required param: amount.",
                     stripeException.getStripeError()
                                    .getMessage());
    }

    @Test
    void shouldRejectPaymentIntentWithoutCurrency() {
        StripeException stripeException = assertThrows(StripeException.class,
                                                       () -> PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                                           .setAmount(10_00L)
                                                                                                           .build()));
        assertEquals("Missing required param: currency.",
                     stripeException.getStripeError()
                                    .getMessage());
    }

    @Test
    void shouldDisallowConfirmWithoutPaymentMethod() throws StripeException {
        PaymentIntent pi = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .setTransferGroup("my_transfer_group")
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .build());
        StripeException stripeException = assertThrows(StripeException.class, pi::confirm);
        assertEquals(
                "You cannot confirm this PaymentIntent because it's missing a payment method. You can either update the PaymentIntent with a payment method and then confirm it again, or confirm it again directly with a payment method.",
                stripeException.getStripeError()
                               .getMessage());
    }

    @Test
    void shouldBeAbleToSetPaymentMethodAndConfirmInSeparateCalls() throws StripeException {
        PaymentIntent pi = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .setTransferGroup("my_transfer_group")
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .build());
        pi.update(PaymentIntentUpdateParams.builder()
                                           .setPaymentMethod("pm_card_mastercard")
                                           .build());
        pi.confirm();
        assertEquals("succeeded",
                     PaymentIntent.retrieve(pi.getId())
                                  .getStatus());

        // shouldn't be able to cancel confirmed payment methods
        StripeException stripeException = assertThrows(StripeException.class, pi::cancel);
        assertEquals(
                "You cannot cancel this PaymentIntent because it has a status of succeeded. Only a PaymentIntent with one of the following statuses may be canceled: requires_payment_method, requires_capture, requires_confirmation, requires_action, processing.",
                stripeException.getStripeError()
                               .getMessage());

    }

    @Test
    void shouldCancelPaymentIntent() throws StripeException {
        PaymentIntent pi = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .build());
        assertEquals("canceled",
                     pi.cancel()
                       .getStatus());
        assertEquals("canceled",
                     PaymentIntent.retrieve(pi.getId())
                                  .getStatus());
    }

    @Test
    void shouldConfirmWithPaymentMethod() throws StripeException {
        PaymentIntent pi = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .build());
        pi.confirm(PaymentIntentConfirmParams.builder()
                                             .setPaymentMethod("pm_card_mastercard")
                                             .build());
        assertEquals("succeeded",
                     PaymentIntent.retrieve(pi.getId())
                                  .getStatus());

        // shouldn't be able to cancel confirmed payment methods
        StripeException stripeException = assertThrows(StripeException.class, pi::cancel);
        assertEquals(
                "You cannot cancel this PaymentIntent because it has a status of succeeded. Only a PaymentIntent with one of the following statuses may be canceled: requires_payment_method, requires_capture, requires_confirmation, requires_action, processing.",
                stripeException.getStripeError()
                               .getMessage());
    }

    @Test
    void shouldHandleEmptyUpdate() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_000L)
                                                                                    .setCurrency("USD")
                                                                                    .build());
        PaymentIntent updated = paymentIntent.update(PaymentIntentUpdateParams.builder()
                                                                              .build());
        assertEquals(paymentIntent, updated);
    }

    @Test
    void shouldConfirmPaymentIntentWithoutACustomer() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .build());
        PaymentIntent confirmed = paymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                                                  .setPaymentMethod("pm_card_mastercard")
                                                                                  .build());
        assertEquals("succeeded", confirmed.getStatus());
    }
}
