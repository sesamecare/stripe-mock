package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.BalanceTransactionListParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;

public class BalanceTransactionTest extends AbstractStripeMockTest {


    @Test
    void shouldListWithExpansions() throws StripeException {
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


        TransferReversal transferReversal = createdTransfer.getReversals()
                                                           .create(TransferReversalCollectionCreateParams.builder()
                                                                                                         .build());

        // todo: set up things where these expands actually work, and then assert that they have been correctly expanded

        BalanceTransactionCollection balanceTransactions = BalanceTransaction.list(BalanceTransactionListParams.builder()
                                                                                                               .setLimit(100L)
                                                                                                               .addAllExpand(List.of(
                                                                                                                       "data.source.source_transfer",
                                                                                                                       "data.source.source_transfer_reversal",
                                                                                                                       "data.source.source_transfer_reversal.transfer"))
                                                                                                               .build(),
                                                                                   RequestOptions.builder()
                                                                                                 .setStripeAccount(account.getId())
                                                                                                 .build());
        // todo: when listing the transfer reversal, the amount should be negative in this case.
        //  Yes, it looks like the "direction", i.e. negative or positive, is in the context of the stripe account.
        System.out.println("balanceTransactions = " + balanceTransactions.getData());
    }
}
