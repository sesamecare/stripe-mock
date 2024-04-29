package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class RefundTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .build());
        paymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                        .setPaymentMethod("pm_card_mastercard")
                                                        .build());
        RefundCreateParams input = RefundCreateParams.builder()
                                                     .setPaymentIntent(paymentIntent.getId())
                                                     .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Refund r1 = Refund.create(input, options);
        Refund r2 = Refund.create(input, options);
        assertEquals(r1, r2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        PaymentIntent pi1 = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                          .setAmount(10_00L)
                                                                          .setCurrency("usd")
                                                                          .putMetadata("integration_test", "true")
                                                                          .build());
        pi1.confirm(PaymentIntentConfirmParams.builder()
                                              .setPaymentMethod("pm_card_mastercard")
                                              .build());

        PaymentIntent pi2 = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                          .setAmount(10_00L)
                                                                          .setCurrency("usd")
                                                                          .putMetadata("integration_test", "true")
                                                                          .build());
        pi2.confirm(PaymentIntentConfirmParams.builder()
                                              .setPaymentMethod("pm_card_mastercard")
                                              .build());

        String idempotencyKey = String.valueOf(Math.random());
        Refund.create(RefundCreateParams.builder()
                                        .setPaymentIntent(pi1.getId())
                                        .build(),
                      RequestOptions.builder()
                                    .setIdempotencyKey(idempotencyKey)
                                    .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Refund.create(RefundCreateParams.builder()
                                                                                                       .setPaymentIntent(pi2.getId())
                                                                                                       .putMetadata("integration_test", "true")
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

    @Test
    void shouldNotRefundPaymentIntentThatIsNotYetSuccessful() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .build());

        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class,
                                                                       () -> Refund.create(RefundCreateParams.builder()
                                                                                                             .setPaymentIntent(paymentIntent.getId())
                                                                                                             .putMetadata("integration_test", "true")
                                                                                                             .build()));
        assertEquals(String.format("This PaymentIntent (%s) does not have a successful charge to refund.", paymentIntent.getId()),
                     invalidRequestException.getStripeError()
                                            .getMessage());

    }

    // todo: test: com.stripe.exception.InvalidRequestException: Refund amount ($100.00) is greater than charge amount ($10.00)

    @Test
    void testRefund() throws Exception {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .build());
        paymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                        .setPaymentMethod("pm_card_mastercard")
                                                        .build());
        Refund createdRefund = //
                Refund.create(RefundCreateParams.builder()
                                                .setPaymentIntent(paymentIntent.getId())
                                                .putMetadata("integration_test", "true")
                                                .setAmount(5_00L)
                                                //.setOrigin(RefundCreateParams.Origin.CUSTOMER_BALANCE) // todo: origin is unsupported, or potentially only in combination with other settings
                                                .setReason(RefundCreateParams.Reason.DUPLICATE)
                                                // todo: make sure that reversing the transfer does what people would expect it to do
                                                //.setReverseTransfer(true)
                                                .build());

        Refund retrievedRefund = Refund.retrieve(createdRefund.getId());
        assertEquals(createdRefund, retrievedRefund);

        Refund updatedRefund = //
                retrievedRefund.update(RefundUpdateParams.builder()
                                                         .putMetadata("integration_test", "false")
                                                         .build());

        Refund retrievedUpdatedRefund = Refund.retrieve(createdRefund.getId());
        assertEquals(updatedRefund, retrievedUpdatedRefund);

        // todo: assert statuses etc.
        //  You can't cancel a card refund, apparently. They can only be pending, succeeded, or failed. As we won't go into the pending state for this test, we won't be able to cancel things probably
        //  But try to replicate this logic
        // todo: test: com.stripe.exception.InvalidRequestException: Charges must be refunded using the /v1/charges/{CHARGE_ID}/refunds endpoint.
        /*
        Refund canceledRefund = retrievedRefund.cancel();
        assertEquals("canceled", canceledRefund.getStatus());
        Refund retrievedCanceledRefund = Refund.retrieve(retrievedRefund.getId());
        assertEquals(canceledRefund, retrievedCanceledRefund);
         */
        // todo: make sure we set custom things like connected transfers
    }

    @Test
    void shouldThrowOnWrongPaymentIntent() {
        StripeException wrongPaymentIntent = assertThrows(StripeException.class,
                                                          () -> Refund.create(RefundCreateParams.builder()
                                                                                                .setPaymentIntent("pi_nope")
                                                                                                .build()));
        assertEquals("No such payment_intent: 'pi_nope'",
                     wrongPaymentIntent.getStripeError()
                                       .getMessage());
    }

    @Test
    void shouldThrowOnMissingPaymentIntent() {
        StripeException missingPaymentIntent = assertThrows(StripeException.class,
                                                            () -> Refund.create(RefundCreateParams.builder()
                                                                                                  .build()));
        assertEquals("One of the following params should be provided for this request: payment_intent or charge.",
                     missingPaymentIntent.getStripeError()
                                         .getMessage());
    }

    @Test
    void shouldListByPaymentIntentId() throws StripeException {
        PaymentIntent p1 = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                         .setAmount(10_00L)
                                                                         .setCurrency("usd")
                                                                         .putMetadata("integration_test", "true")
                                                                         .build())
                                        .confirm(PaymentIntentConfirmParams.builder()
                                                                           .setPaymentMethod("pm_card_mastercard")
                                                                           .build());

        Refund r1 = Refund.create(RefundCreateParams.builder()
                                                    .setPaymentIntent(p1.getId())
                                                    .setReason(RefundCreateParams.Reason.DUPLICATE)
                                                    .putMetadata("integration_test", "true")
                                                    .build());

        PaymentIntent p2 = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                         .setAmount(10_00L)
                                                                         .setCurrency("usd")
                                                                         .putMetadata("integration_test", "true")
                                                                         .build())
                                        .confirm(PaymentIntentConfirmParams.builder()
                                                                           .setPaymentMethod("pm_card_mastercard")
                                                                           .build());

        Refund r21 = Refund.create(RefundCreateParams.builder()
                                                     .setAmount(1_00L)
                                                     .setPaymentIntent(p2.getId())
                                                     .setReason(RefundCreateParams.Reason.DUPLICATE)
                                                     .putMetadata("integration_test", "true")
                                                     .build());
        Refund r22 = Refund.create(RefundCreateParams.builder()
                                                     .setAmount(2_00L)
                                                     .setPaymentIntent(p2.getId())
                                                     .setReason(RefundCreateParams.Reason.DUPLICATE)
                                                     .putMetadata("integration_test", "true")
                                                     .build());

        List<Refund> rs1 = Refund.list(RefundListParams.builder()
                                                       .setPaymentIntent(p1.getId())
                                                       .build())
                                 .getData();

        assertEquals(Collections.singletonList(r1), rs1);

        List<Refund> rs2 = Refund.list(RefundListParams.builder()
                                                       .setPaymentIntent(p2.getId())
                                                       .build())
                                 .getData();

        // order needs to be deterministic for a list comparison, but it isn't, so we have to sort it first
        assertEquals(Stream.of(r21, r22)
                           .sorted(Comparator.comparing(Refund::getId))
                           .toList(),
                     rs2.stream()
                        .sorted(Comparator.comparing(Refund::getId))
                        .toList());
    }

    @Test
    void shouldGetRefundAmountFromPaymentIntentIfNotExplicitlySpecified() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .build());
        paymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                        .setPaymentMethod("pm_card_mastercard")
                                                        .build());
        Refund createdRefund = //
                Refund.create(RefundCreateParams.builder()
                                                .setPaymentIntent(paymentIntent.getId())
                                                .build());
        assertEquals(10_00L, createdRefund.getAmount());
    }

    @Test
    void shouldRefundACharge() throws StripeException {
        Charge charge = Charge.create(ChargeCreateParams.builder()
                                                        .setAmount(10_00L)
                                                        .setCurrency("usd")
                                                        .putMetadata("integration_test", "true")
                                                        .build());
        Refund refund = Refund.create(RefundCreateParams.builder()
                                                        .setCharge(charge.getId())
                                                        .putMetadata("integration_test", "true")
                                                        .build());
        assertEquals("succeeded", refund.getStatus());
        Charge retrievedCharge = Charge.retrieve(charge.getId());
        assertTrue(retrievedCharge.getRefunded());
        assertEquals(10_00L, retrievedCharge.getAmountRefunded());
        List<Refund> refunds = retrievedCharge.getRefunds()
                                              .getData();
        assertEquals(1, refunds.size());
        assertEquals(10_00L,
                     refunds.getFirst()
                            .getAmount());
    }

    @Test
    void shouldPartiallyRefundACharge() throws StripeException {
        Charge charge = Charge.create(ChargeCreateParams.builder()
                                                        .setAmount(10_00L)
                                                        .setCurrency("usd")
                                                        .putMetadata("integration_test", "true")
                                                        .build());
        Refund refund1 = Refund.create(RefundCreateParams.builder()
                                                         .setCharge(charge.getId())
                                                         .setAmount(5_00L)
                                                         .putMetadata("integration_test", "true")
                                                         .build());
        assertEquals("succeeded", refund1.getStatus());
        Charge retrievedCharge = Charge.retrieve(charge.getId());
        assertFalse(retrievedCharge.getRefunded());
        assertEquals(5_00L, retrievedCharge.getAmountRefunded());
        List<Refund> refunds = retrievedCharge.getRefunds()
                                              .getData();
        assertEquals(1, refunds.size());
        assertEquals(5_00L,
                     refunds.getFirst()
                            .getAmount());

        // refund the last part so that it is fully refunded
        Refund refund2 = Refund.create(RefundCreateParams.builder()
                                                         .setCharge(charge.getId())
                                                         .setAmount(5_00L)
                                                         .putMetadata("integration_test", "true")
                                                         .build());
        assertEquals("succeeded", refund2.getStatus());
        Charge retrievedCharge2 = Charge.retrieve(charge.getId());
        assertTrue(retrievedCharge2.getRefunded());
        assertEquals(10_00L, retrievedCharge2.getAmountRefunded());
        List<Refund> refunds2 = retrievedCharge2.getRefunds()
                                                .getData();
        assertEquals(2, refunds2.size());
        assertEquals(5_00L,
                     refunds2.getFirst()
                             .getAmount());
        assertEquals(5_00L,
                     refunds2.getLast()
                             .getAmount());
    }

    // todo: test cancelling refunds. How can they be in a state where we can even cancel them?
}
