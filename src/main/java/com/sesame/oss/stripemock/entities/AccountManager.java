package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Account;
import com.stripe.model.BankAccount;
import com.stripe.model.ExternalAccountCollection;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

class AccountManager extends AbstractEntityManager<Account> {
    protected AccountManager(Clock clock, StripeEntities stripeEntities) {
        super(stripeEntities, clock, Account.class, "acct", 24);
    }

    @Override
    protected Account parse(Map<String, Object> formData) {
        Object capabilities = formData.get("capabilities");
        if (capabilities instanceof Map m) {
            // Technically this could be "pending", "active", or "requested", but since we don't have a process for moving things forward,
            // we're going to move all the capabilities straight into "active".
            m.replaceAll((k, v) -> "active");
        }
        return super.parse(formData);
    }

    @Override
    protected Account initialize(Account account, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        ExternalAccountCollection externalAccounts = new ExternalAccountCollection();
        externalAccounts.setObject("list");
        externalAccounts.setHasMore(false);
        externalAccounts.setData(new ArrayList<>());
        externalAccounts.setUrl("/v1/accounts/" + account.getId() + "/external_accounts");
        account.setExternalAccounts(externalAccounts);

        Map<String, Object> externalAccountFormData = (Map<String, Object>) formData.get("external_account");
        if (externalAccountFormData != null) {
            BankAccount bankAccount = stripeEntities.getEntityManager(BankAccount.class)
                                                    .add(externalAccountFormData, stripeAccount);
            bankAccount.setAccount(account.getId());
            externalAccounts.getData()
                            .add(bankAccount);
            stripeEntities.bindChildToParentCollection(Account.class, account.getId(), "getExternalAccounts", bankAccount.getId());
        }
        return super.initialize(account, formData, stripeAccount);
    }

    @Override
    public Optional<Account> get(String id, String stripeAccount) throws ResponseCodeException {
        Optional<Account> account = super.get(id, stripeAccount);
        if (account.isEmpty()) {
            // Accounts are special like this, in that they return a 403 instead of a 404
            throw new ResponseCodeException(403,
                                            String.format(
                                                    "The provided key 'sk_test_***********************************************************************************************fake' does not have access to account '%s' (or that account does not exist). Application access may have been revoked.",
                                                    id),
                                            "account_invalid",
                                            "invalid_request_error",
                                            null,
                                            null);
        }
        return account;
    }

    @Override
    public Optional<Account> delete(String id) {
        Account account = entities.remove(id);
        if (account == null) {
            return Optional.empty();
        }
        // unlike most other things that can be deleted, accounts can no longer be retrieved.
        account.setDeleted(true);
        return Optional.of(account);
    }
}
