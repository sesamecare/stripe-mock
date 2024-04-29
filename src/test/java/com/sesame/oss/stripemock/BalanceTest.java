package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Balance;
import com.stripe.net.RequestOptions;
import com.stripe.param.BalanceRetrieveParams;
import org.junit.jupiter.api.Test;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;

public class BalanceTest extends AbstractStripeMockTest {
    @Test
    void shouldFetchEmptyBalanceForConnectAccount() throws StripeException {
        Account createdAccount = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Balance balance = Balance.retrieve(BalanceRetrieveParams.builder()
                                                                .build(),
                                           RequestOptions.builder()
                                                         .setStripeAccount(createdAccount.getId())
                                                         .build());
        /*
        Defaults with nothing in it:

        {
          "available": [
            {
              "amount": 0,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 0,
                "fpx": null
              }
            }
          ],
          "connect_reserved": null,
          "instant_available": [
            {
              "amount": 0,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 0,
                "fpx": null
              }
            }
          ],
          "issuing": null,
          "livemode": false,
          "object": "balance",
          "pending": [
            {
              "amount": 0,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 0,
                "fpx": null
              }
            }
          ]
        }
         */
        System.out.println("balance = " + balance);
    }

    @Test
    void shouldFetchEmptyBalanceForMainAccount() throws StripeException {
        Balance balance = Balance.retrieve();
        /*
        Defaults with nothing in it:

        {
          "available": [
            {
              "amount": 15766637,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 15766637,
                "fpx": null
              }
            }
          ],
          "connect_reserved": [
            {
              "amount": 12649356,
              "currency": "usd",
              "source_types": null
            }
          ],
          "instant_available": [
            {
              "amount": 1000000,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 1000000,
                "fpx": null
              }
            }
          ],
          "issuing": null,
          "livemode": false,
          "object": "balance",
          "pending": [
            {
              "amount": 863489,
              "currency": "usd",
              "source_types": {
                "bank_account": null,
                "card": 863489,
                "fpx": null
              }
            }
          ]
        }
         */
        System.out.println("balance = " + balance);
    }
}
