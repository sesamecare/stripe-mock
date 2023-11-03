package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Account;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

class AccountManager extends AbstractEntityManager<Account> {
    protected AccountManager(Clock clock) {
        super(clock, Account.class, "acct");
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
    public Optional<Account> get(String id) throws ResponseCodeException {
        Optional<Account> account = super.get(id);
        if (account.isEmpty()) {
            // Accounts are special like this, in that they return a 403 instead of a 404
            throw new ResponseCodeException(403,
                                            String.format(
                                                    "The provided key 'sk_test_***********************************************************************************************fake' does not have access to account '%s' (or that account does not exist). Application access may have been revoked.",
                                                    id),
                                            "account_invalid",
                                            "invalid_request_error",
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
