package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Transfer;
import com.stripe.model.TransferCollection;
import com.stripe.model.TransferReversal;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferListParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import com.stripe.param.TransferUpdateParams;
import org.junit.jupiter.api.Test;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static org.junit.jupiter.api.Assertions.*;

public class TransferTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        TransferCreateParams input = TransferCreateParams.builder()
                                                         .setAmount(10_000L)
                                                         .setCurrency("USD")
                                                         .setDestination(account.getId())
                                                         .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Transfer t1 = Transfer.create(input, options);
        Transfer t2 = Transfer.create(input, options);
        assertEquals(t1, t2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        String idempotencyKey = String.valueOf(Math.random());
        Transfer.create(TransferCreateParams.builder()
                                            .setAmount(10_00L)
                                            .setCurrency("usd")
                                            .setDestination(account.getId())
                                            .build(),
                        RequestOptions.builder()
                                      .setIdempotencyKey(idempotencyKey)
                                      .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Transfer.create(TransferCreateParams.builder()
                                                                                                           .setAmount(10_00L)
                                                                                                           .setCurrency("eur")
                                                                                                           .setDestination(account.getId())
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
    void shouldRejectTransfersWithoutADestination() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                                                         () -> Transfer.create(TransferCreateParams.builder()
                                                                                                   .setAmount(9_000L)
                                                                                                   .setCurrency("usd")
                                                                                                   .putMetadata("integration_test", "true")
                                                                                                   .setTransferGroup("my transfer group")
                                                                                                   .setSourceType(TransferCreateParams.SourceType.CARD)
                                                                                                   .setDescription("my description")
                                                                                                   .build()));
        assertEquals("destination", exception.getParam());
    }

    @Test
    void shouldExpandReversalsWhenListingIfRequested() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer createdTransfer = //
                Transfer.create(TransferCreateParams.builder()
                                                    .setAmount(9_000L)
                                                    .setCurrency("usd")
                                                    .putMetadata("integration_test", "true")
                                                    .setTransferGroup("my transfer group")
                                                    .setSourceType(TransferCreateParams.SourceType.CARD)
                                                    .setDescription("my description")
                                                    .setDestination(account.getId())
                                                    .build());

        TransferCollection transfersBeforeReversals = Transfer.list(TransferListParams.builder()
                                                                                      .setDestination(account.getId())
                                                                                      .setLimit(100L)
                                                                                      .addExpand("data.reversals")
                                                                                      .build());
        assertTrue(transfersBeforeReversals.getData()
                                           .getFirst()
                                           .getReversals()
                                           .getData()
                                           .isEmpty());

        TransferReversal transferReversal = createdTransfer.getReversals()
                                                           .create(TransferReversalCollectionCreateParams.builder()
                                                                                                         .build());

        TransferCollection transfersAfterReversals = Transfer.list(TransferListParams.builder()
                                                                                     .setDestination(account.getId())
                                                                                     .setLimit(100L)
                                                                                     .addExpand("data.reversals")
                                                                                     .build());
        // Make sure it's actually expanded, and we're not just getting the id.
        assertEquals(transferReversal.getAmount(),
                     transfersAfterReversals.getData()
                                            .getFirst()
                                            .getReversals()
                                            .getData()
                                            .getFirst()
                                            .getAmount());
    }

    // todo: tests for missing destination, with error: com.stripe.exception.InvalidRequestException: Missing required param: destination.; code: parameter_missing
    // todo: test: com.stripe.exception.InvalidRequestException: Missing required param: currency.; code: parameter_missing

    @Test
    void testTransfer() throws Exception {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer createdTransfer = //
                Transfer.create(TransferCreateParams.builder()
                                                    .setAmount(9_000L)
                                                    .setCurrency("usd")
                                                    .putMetadata("integration_test", "true")
                                                    .setTransferGroup("my transfer group")
                                                    .setSourceType(TransferCreateParams.SourceType.CARD)
                                                    .setDescription("my description")
                                                    .setDestination(account.getId())
                                                    .build());

        Transfer retrievedTransfer = Transfer.retrieve(createdTransfer.getId());
        assertEquals(createdTransfer, retrievedTransfer);

        Transfer updatedTransfer = //
                retrievedTransfer.update(TransferUpdateParams.builder()
                                                             .setDescription("my description")
                                                             .build());

        Transfer retrievedUpdatedTransfer = Transfer.retrieve(createdTransfer.getId());
        assertEquals(updatedTransfer, retrievedUpdatedTransfer);

        TransferReversal transferReversal = retrievedTransfer.getReversals()
                                                             .create(TransferReversalCollectionCreateParams.builder()
                                                                                                           .build());
        assertEquals(9_000L, transferReversal.getAmount());
        assertEquals(createdTransfer.getId(), transferReversal.getTransfer());
        Transfer retrievedReversedTransfer = Transfer.retrieve(createdTransfer.getId());
        assertTrue(retrievedReversedTransfer.getReversed());
        assertEquals(9_000L,
                     Transfer.retrieve(createdTransfer.getId())
                             .getAmountReversed());

        assertEquals(1,
                     retrievedReversedTransfer.getReversals()
                                              .getData()
                                              .size());


    }

    @Test
    void shouldCreatePartialTransferReversals() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer createdTransfer = //
                Transfer.create(TransferCreateParams.builder()
                                                    .setAmount(10_00L)
                                                    .setCurrency("usd")
                                                    .putMetadata("integration_test", "true")
                                                    .setTransferGroup("my transfer group")
                                                    .setSourceType(TransferCreateParams.SourceType.CARD)
                                                    .setDescription("my description")
                                                    .setDestination(account.getId())
                                                    .build());

        TransferReversal tr1 = createdTransfer.getReversals()
                                              .create(TransferReversalCollectionCreateParams.builder()
                                                                                            .setAmount(5_00L)
                                                                                            .build());
        assertEquals(5_00L, tr1.getAmount());
        assertEquals(createdTransfer.getId(), tr1.getTransfer());

        Transfer t1 = Transfer.retrieve(createdTransfer.getId());
        assertFalse(t1.getReversed());
        assertEquals(5_00L, t1.getAmountReversed());
        assertEquals(5_00L,
                     t1.getReversals()
                       .getData()
                       .stream()
                       .mapToLong(TransferReversal::getAmount)
                       .sum());

        TransferReversal tr2 = createdTransfer.getReversals()
                                              .create(TransferReversalCollectionCreateParams.builder()
                                                                                            .setAmount(5_00L)
                                                                                            .build());
        assertEquals(5_00L, tr2.getAmount());
        assertEquals(createdTransfer.getId(), tr2.getTransfer());

        Transfer t2 = Transfer.retrieve(createdTransfer.getId());
        assertTrue(t2.getReversed());
        assertEquals(10_00L, t2.getAmountReversed());
        assertEquals(10_00L,
                     t2.getReversals()
                       .getData()
                       .stream()
                       .mapToLong(TransferReversal::getAmount)
                       .sum());
    }
}
