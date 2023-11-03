package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferUpdateParams;
import org.junit.jupiter.api.Test;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TransferTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Company name"));
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
        Account account = Account.create(defaultCreationParameters("Company name"));
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

    // todo: tests for missing destination, with error: com.stripe.exception.InvalidRequestException: Missing required param: destination.; code: parameter_missing
    // todo: test: com.stripe.exception.InvalidRequestException: Missing required param: currency.; code: parameter_missing

    @Test
    void testTransfer() throws Exception {
        Account account = Account.create(defaultCreationParameters("Company name"));
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
    }
}
