package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Balance;
import com.stripe.model.Charge;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.BalanceRetrieveParams;
import com.stripe.param.ChargeCreateParams;
import com.stripe.param.TransferCreateParams;
import org.junit.jupiter.api.Test;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BalanceTest extends AbstractStripeMockTest {
    @Test
    void shouldFetchEmptyBalanceForConnectAccount() throws StripeException {
        Account createdAccount = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Balance balance = Balance.retrieve(BalanceRetrieveParams.builder()
                                                                .build(),
                                           RequestOptions.builder()
                                                         .setStripeAccount(createdAccount.getId())
                                                         .build());

        assertEquals(1,
                     balance.getAvailable()
                            .size());
        assertEquals(0,
                     balance.getAvailable()
                            .getFirst()
                            .getAmount());

        assertNull(balance.getConnectReserved());

        assertEquals(1,
                     balance.getInstantAvailable()
                            .size());
        assertEquals(0,
                     balance.getInstantAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getPending()
                            .size());
        assertEquals(0,
                     balance.getPending()
                            .getFirst()
                            .getAmount());

        assertEquals("balance", balance.getObject());
    }

    @Test
    void shouldFetchEmptyBalanceForMainAccount() throws StripeException {
        Balance balance = Balance.retrieve();
        assertEquals(1,
                     balance.getAvailable()
                            .size());
        assertEquals(0,
                     balance.getAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getConnectReserved()
                            .size());
        assertEquals(0,
                     balance.getConnectReserved()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getInstantAvailable()
                            .size());
        assertEquals(0,
                     balance.getInstantAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getPending()
                            .size());
        assertEquals(0,
                     balance.getPending()
                            .getFirst()
                            .getAmount());

        assertEquals("balance", balance.getObject());
    }

    @Test
    void shouldAddAllAvailableBalancesForStripeAccount() throws StripeException {
        Account createdAccount = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer.create(TransferCreateParams.builder()
                                            .setAmount(10_00L)
                                            .setCurrency("usd")
                                            .putMetadata("integration_test", "true")
                                            .setDestination(createdAccount.getId())
                                            .build());

        Transfer.create(TransferCreateParams.builder()
                                            .setAmount(20_00L)
                                            .setCurrency("usd")
                                            .putMetadata("integration_test", "true")
                                            .setDestination(createdAccount.getId())
                                            .build());

        Balance balance = Balance.retrieve(BalanceRetrieveParams.builder()
                                                                .build(),
                                           RequestOptions.builder()
                                                         .setStripeAccount(createdAccount.getId())
                                                         .build());

        assertEquals(1,
                     balance.getAvailable()
                            .size());
        assertEquals(30_00L,
                     balance.getAvailable()
                            .getFirst()
                            .getAmount());

        assertNull(balance.getConnectReserved());

        assertEquals(1,
                     balance.getInstantAvailable()
                            .size());
        assertEquals(0,
                     balance.getInstantAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getPending()
                            .size());
        assertEquals(0,
                     balance.getPending()
                            .getFirst()
                            .getAmount());

        assertEquals("balance", balance.getObject());


        createdAccount.delete();
    }

    @Test
    void shouldAddAllAvailableBalancesForMainAccount() throws StripeException {
        Account createdAccount = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer.create(TransferCreateParams.builder()
                                            .setAmount(10_00L)
                                            .setCurrency("usd")
                                            .putMetadata("integration_test", "true")
                                            .setDestination(createdAccount.getId())
                                            .build());

        Transfer.create(TransferCreateParams.builder()
                                            .setAmount(20_00L)
                                            .setCurrency("usd")
                                            .putMetadata("integration_test", "true")
                                            .setDestination(createdAccount.getId())
                                            .build());

        Charge.create(ChargeCreateParams.builder()
                                        .setAmount(40_00L)
                                        .setCurrency("usd")
                                        .putMetadata("integration_test", "true")
                                        .build());

        Balance balance = Balance.retrieve();

        assertEquals(1,
                     balance.getAvailable()
                            .size());
        assertEquals(10_00L,
                     balance.getAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getConnectReserved()
                            .size());
        assertEquals(0,
                     balance.getConnectReserved()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getInstantAvailable()
                            .size());
        assertEquals(0,
                     balance.getInstantAvailable()
                            .getFirst()
                            .getAmount());

        assertEquals(1,
                     balance.getPending()
                            .size());
        assertEquals(0,
                     balance.getPending()
                            .getFirst()
                            .getAmount());

        assertEquals("balance", balance.getObject());


        createdAccount.delete();
    }
}
