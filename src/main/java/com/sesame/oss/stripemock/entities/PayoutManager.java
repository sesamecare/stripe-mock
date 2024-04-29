package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.*;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PayoutManager extends AbstractEntityManager<Payout> {
    private final Map<String, List<Payout>> byStripeAccount = new HashMap<>();
    private final StripeEntities stripeEntities;

    PayoutManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Payout.class, "po", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public Payout add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        Payout payout = super.add(formData, stripeAccount);
        byStripeAccount.computeIfAbsent(stripeAccount, ignored -> new ArrayList<>())
                       .add(payout);
        return payout;
    }

    @Override
    protected Payout initialize(Payout payout, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {

        // todo: why is there a 'destination' value on the payout when the actual destination is specified un the request options?
        //  Are both actually supported?
        //  Apparently you use destination to specify an explicit bank account, whereas otherwise I guess it'll use the default/only account?
        //  If not specified, it's the default for the specified currency
        Account account = stripeEntities.getEntityManager(Account.class)
                                        .get(stripeAccount, null)
                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(404, "account", stripeAccount));

        List<ExternalAccount> externalAccounts = account.getExternalAccounts()
                                                        .getData();
        // todo: find account by currency or throw
        // todo: find account by payout destination, if provided, or throw

        payout.setStatus("pending");
        payout.setAutomatic(false);
        payout.setMethod("standard");
        payout.setReconciliationStatus("in_progress");

        if (!externalAccounts.isEmpty()) {
            ExternalAccount targetAccount = externalAccounts.getFirst();
            if (targetAccount instanceof BankAccount bankAccount) {

                // todo: make sure it fails when this is the account: 000111111113
                //  Also support other test accounts from stripe
                BankAccountManager bankAccountEntityManager = (BankAccountManager) stripeEntities.getEntityManager(BankAccount.class);
                // todo: clean this whole thing up
                if ("000111111113".equals(bankAccountEntityManager.getAccountNumber(bankAccount.getId())
                                                                  .orElseThrow())) {
                    payout.setStatus("failed");
                } else {
                    // todo: check that there are sufficient funds. If not, reject it.
                    payout.setStatus("paid");

                    payout.setBalanceTransaction(Utilities.randomIdWithPrefix("txn", 24));
                    // By registering this, it can be converted on the fly when expanded or fetched.
                    BalanceTransactionManager balanceTransactionEntityManager =
                            (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
                    balanceTransactionEntityManager.register(payout.getBalanceTransaction(), payout);


                }

                // todo: handle bank accounts like payment methods. Make sure they get their own id, and then look up that id to see what behavior we should use.

            /*
            <com.stripe.model.BankAccount@1194457333 id=ba_1P6ochFKdO3zyWmDnRMlQzOo> JSON: {
                  "account": "acct_1P6ocgFKdO3zyWmD",
                  "account_holder_name": null,
                  "account_holder_type": null,
                  "account_type": null,
                  "available_payout_methods": [
                    "standard",
                    "instant"
                  ],
                  "bank_name": "STRIPE TEST BANK",
                  "country": "US",
                  "currency": "usd",
                  "customer": null,
                  "default_for_currency": true,
                  "deleted": null,
                  "fingerprint": "FCuANXhMD3cP7T3X",
                  "future_requirements": {
                    "currently_due": [],
                    "errors": [],
                    "past_due": [],
                    "pending_verification": []
                  },
                  "id": "ba_1P6ochFKdO3zyWmDnRMlQzOo",
                  "last4": "6789",
                  "metadata": {},
                  "object": "bank_account",
                  "requirements": {
                    "currently_due": [],
                    "errors": [],
                    "past_due": [],
                    "pending_verification": []
                  },
                  "routing_number": "110000000",
                  "status": "new"
                }
             */

            }
        } else {
            // todo: what happens if we do a payout if there is no bank account or card?
        }


        return super.initialize(payout, formData, stripeAccount);
    }

    @Override
    public List<Payout> list(QueryParameters query, String stripeAccount) {
        if (stripeAccount == null) {
            // todo: should this be allowed to happen? How does stripe handle this?
            return entities.values()
                           .stream()
                           .toList();
        } else {
            return byStripeAccount.getOrDefault(stripeAccount, new ArrayList<>());
        }
    }

    @Override
    public void clear() {
        super.clear();
        byStripeAccount.clear();
    }

    // todo: tests for Payout, including source
}
