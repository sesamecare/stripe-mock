package com.sesame.oss.stripemock;

import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Payout;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.PayoutCreateParams;
import com.stripe.param.TransferCreateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PayoutTest extends AbstractStripeMockTest {
    @Test
    void testPayouts() throws StripeException {
        Account account = Account.create(AccountTest.defaultCreationParameters("Stripe-mock test company"));
        Transfer t1 = Transfer.create(TransferCreateParams.builder()
                                                          .setAmount(11_00L)
                                                          .setDestination(account.getId())
                                                          .setCurrency("usd")
                                                          .build());
        Transfer t2 = Transfer.create(TransferCreateParams.builder()
                                                          .setAmount(22_00L)
                                                          .setDestination(account.getId())
                                                          .setCurrency("usd")
                                                          .build());

        Payout payout = Payout.create(PayoutCreateParams.builder()
                                                        .setAmount(30_00L)
                                                        .setCurrency("usd")
                                                        .build(),
                                      RequestOptions.builder()
                                                    .setStripeAccount(account.getId())
                                                    .build());

        assertEquals("paid", payout.getStatus());
        account.delete();
    }

    @Test
    void shouldFailPayoutOnInsufficientBalance() throws StripeException {
        Account account = Account.create(AccountTest.defaultCreationParameters("Stripe-mock test company"));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class,
                                                         () -> Payout.create(PayoutCreateParams.builder()
                                                                                               .setAmount(30_00L)
                                                                                               .setCurrency("usd")
                                                                                               .build(),
                                                                             RequestOptions.builder()
                                                                                           .setStripeAccount(account.getId())
                                                                                           .build()));
        assertTrue(exception.getMessage()
                            .contains("You have insufficient funds in your Stripe account for this transfer."));
        assertEquals("balance_insufficient", exception.getCode());


        account.delete();
    }

    @Test
    void shouldFailPayoutOnInvalidBankAccount() throws StripeException {
        Account account = Account.create(AccountTest.defaultCreationParameters("Stripe-mock test company"));
        ExternalAccountTest.replaceExternalAccountWith(account, "000111111113");

        Payout payout = Payout.create(PayoutCreateParams.builder()
                                                        .setAmount(30_00L)
                                                        .setCurrency("usd")
                                                        .build(),
                                      RequestOptions.builder()
                                                    .setStripeAccount(account.getId())
                                                    .build());

        assertEquals("failed", payout.getStatus());
        account.delete();
    }
}
