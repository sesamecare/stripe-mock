package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.BalanceTransactionListParams;
import com.stripe.param.PayoutCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.TransferReversalCollectionCreateParams;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static org.junit.jupiter.api.Assertions.*;

public class BalanceTransactionTest extends AbstractStripeMockTest {


    @Test
    void shouldListWithExpansions() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer rtr = //
                Transfer.create(TransferCreateParams.builder()
                                                    .setAmount(10_00L)
                                                    .setCurrency("usd")
                                                    .putMetadata("integration_test", "true")
                                                    .setTransferGroup("my transfer group")
                                                    .setSourceType(TransferCreateParams.SourceType.CARD)
                                                    .setDescription("my description")
                                                    .setDestination(account.getId())
                                                    .build());

        StripeMock.adjustTimeTo(StripeMock.getClock()
                                          .instant()
                                          .plusSeconds(10));


        TransferReversal trr = rtr.getReversals()
                                  .create(TransferReversalCollectionCreateParams.builder()
                                                                                .build());
        StripeMock.adjustTimeTo(StripeMock.getClock()
                                          .instant()
                                          .plusSeconds(10));

        Transfer tr = Transfer.create(TransferCreateParams.builder()
                                                          .setAmount(40_00L)
                                                          .setCurrency("usd")
                                                          .putMetadata("integration_test", "true")
                                                          .setDestination(account.getId())
                                                          .build());

        StripeMock.adjustTimeTo(StripeMock.getClock()
                                          .instant()
                                          .plusSeconds(10));

        Payout po = Payout.create(PayoutCreateParams.builder()
                                                    .setAmount(30_00L)
                                                    .setCurrency("usd")
                                                    .putMetadata("integration_test", "true")
                                                    .build(),
                                  RequestOptions.builder()
                                                .setStripeAccount(account.getId())
                                                .build());

        BalanceTransactionCollection result = BalanceTransaction.list(BalanceTransactionListParams.builder()
                                                                                                  .setLimit(100L)
                                                                                                  .addAllExpand(List.of("data.source.source_transfer",
                                                                                                                        "data.source.source_transfer_reversal",
                                                                                                                        "data.source.source_transfer_reversal.transfer"))
                                                                                                  .build(),
                                                                      RequestOptions.builder()
                                                                                    .setStripeAccount(account.getId())
                                                                                    .build());
        assertEquals("list", result.getObject());
        assertFalse(result.getHasMore());
        List<BalanceTransaction> balanceTransactions = result.getData();
        assertEquals(4, balanceTransactions.size());
        assertTrue(balanceTransactions.stream()
                                      .map(BalanceTransaction::getStatus)
                                      .allMatch(status -> status.equals("available")));

        BalanceTransaction btTrr = balanceTransactions.get(2);
        assertEquals("REFUND FOR PAYMENT", btTrr.getDescription());
        assertEquals("refund", btTrr.getReportingCategory());
        assertEquals("payment_refund", btTrr.getType());
        assertEquals(-1000, btTrr.getAmount());
        assertEquals(-1000, btTrr.getNet());
        if (btTrr.getSourceObject() instanceof Refund refund) {
            assertEquals(1000, refund.getAmount());
            assertTrue(refund.getId()
                             .startsWith("pyr_"));
            assertTrue(refund.getCharge()
                             .startsWith("py_"));
            assertNull(refund.getChargeObject());
            assertNull(refund.getBalanceTransactionObject()); // otherwise we'll get a cycle
            assertEquals(1000,
                         refund.getSourceTransferReversalObject()
                               .getAmount());
            assertNull(refund.getTransferReversalObject());
        } else {
            fail("Not a refund");
        }


        BalanceTransaction btRtr = balanceTransactions.get(3);
        assertNull(btRtr.getDescription());
        assertEquals("charge", btRtr.getReportingCategory());
        assertEquals("payment", btRtr.getType());
        assertEquals(1000, btRtr.getAmount());
        assertEquals(1000, btRtr.getNet());
        if (btRtr.getSourceObject() instanceof Charge charge) {
            assertEquals(1000, charge.getAmount());
            assertTrue(charge.getId()
                             .startsWith("py_"));
            assertTrue(charge.getCaptured());
            assertEquals("charge", charge.getObject());
            assertEquals("my description",
                         charge.getSourceTransferObject()
                               .getDescription());
        } else {
            fail("Not a charge");
        }

        BalanceTransaction btTr = balanceTransactions.get(1);
        assertNull(btTr.getDescription());
        assertEquals("charge", btTr.getReportingCategory());
        assertEquals("payment", btTr.getType());
        assertEquals(4000, btTr.getAmount());
        assertEquals(4000, btTr.getNet());
        if (btTr.getSourceObject() instanceof Charge charge) {
            assertEquals(4000, charge.getAmount());
            assertTrue(charge.getId()
                             .startsWith("py_"));
            assertTrue(charge.getCaptured());
            assertEquals("charge", charge.getObject());
            assertNull(charge.getSourceTransferObject()
                             .getDescription());
            assertTrue(charge.getSourceTransferObject()
                             .getId()
                             .startsWith("tr_"));
        } else {
            fail("Not a charge");
        }

        BalanceTransaction btPo = balanceTransactions.get(0);
        assertNull(btPo.getDescription());
        assertEquals("payout", btPo.getReportingCategory());
        assertEquals("payout", btPo.getType());
        assertEquals(-3000, btPo.getAmount());
        assertEquals(-3000, btPo.getNet());
        if (btPo.getSourceObject() instanceof Payout payout) {
            assertEquals(3000, payout.getAmount());
            assertEquals("standard", payout.getMethod());
        } else {
            fail("Not a payout");
        }
    }
}
