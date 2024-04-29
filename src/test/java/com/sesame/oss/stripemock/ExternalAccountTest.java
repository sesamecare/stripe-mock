package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;
import com.stripe.model.ExternalAccount;
import com.stripe.param.ExternalAccountCollectionListParams;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static com.sesame.oss.stripemock.AccountTest.externalAccountData;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExternalAccountTest extends AbstractStripeMockTest {
    @Test
    void shouldAttachNewExternalAccount() throws StripeException {
        Account createdAccount = //
                Account.create(defaultCreationParameters("Stripe-mock test company name"));
        ExternalAccount newAccount = replaceExternalAccountWith(createdAccount, "000111111113");
        assertEquals(createdAccount.getId(), ((BankAccount) newAccount).getAccount());
        ExternalAccount retrievedExternalAccount = createdAccount.getExternalAccounts()
                                                                 .retrieve(newAccount.getId());
        assertEquals(newAccount, retrievedExternalAccount);
        createdAccount.delete();
    }

    public static ExternalAccount replaceExternalAccountWith(Account account, String accountNumber) throws StripeException {
        // Add the new one
        ExternalAccount newAccount = account.getExternalAccounts()
                                            .create(Collections.singletonMap("external_account", externalAccountData(accountNumber, true)));

        // Delete all the other ones
        for (ExternalAccount externalAccount : account.getExternalAccounts()
                                                      .list(ExternalAccountCollectionListParams.builder()
                                                                                               .build())
                                                      .autoPagingIterable()) {
            if (externalAccount.getId()
                               .equals(newAccount.getId())) {
                // keep this one that we just created
                continue;
            }
            externalAccount.delete();
        }
        return newAccount;
    }
}
