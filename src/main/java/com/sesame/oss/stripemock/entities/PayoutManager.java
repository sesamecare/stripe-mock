package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.BalanceUtilities;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.*;

import java.time.Clock;
import java.util.*;

class PayoutManager extends AbstractEntityManager<Payout> {
    private final Map<String, Set<String>> stripeAccountToPayoutId = new HashMap<>();

    PayoutManager(Clock clock, StripeEntities stripeEntities) {
        super(stripeEntities, clock, Payout.class, "po", 24);
    }

    @Override
    public Payout add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        Payout payout = super.add(formData, stripeAccount);
        stripeAccountToPayoutId.computeIfAbsent(stripeAccount, ignored -> new HashSet<>())
                               .add(payout.getId());
        return payout;
    }

    @Override
    protected Payout initialize(Payout payout, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        Account account = stripeEntities.getEntityManager(Account.class)
                                        .get(stripeAccount, null)
                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(404, "account", stripeAccount));

        List<ExternalAccount> externalAccounts = account.getExternalAccounts()
                                                        .getData();
        // todo: find account by currency or throw
        // todo: find account by payout destination, if provided, or throw
        // todo: support specifying the destination in the payout object. If not present, we'll use the default for the specified currency

        payout.setStatus("pending");
        payout.setAutomatic(false);
        payout.setMethod("standard");
        payout.setReconciliationStatus("in_progress");

        if (!externalAccounts.isEmpty()) {
            ExternalAccount targetAccount = externalAccounts.getFirst();
            if (targetAccount instanceof BankAccount bankAccount) {

                BankAccountManager bankAccountEntityManager = (BankAccountManager) stripeEntities.getEntityManager(BankAccount.class);
                // todo: clean this whole thing up
                if ("000111111113".equals(bankAccountEntityManager.getAccountNumber(bankAccount.getId())
                                                                  .orElseThrow())) {
                    // todo: support other test accounts from stripe
                    payout.setStatus("failed");
                } else {
                    payout.setStatus("paid");

                    BalanceTransactionManager balanceTransactionEntityManager =
                            (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
                    long sumAvailable = BalanceUtilities.sum(balanceTransactionEntityManager.list(null, stripeAccount), "available");
                    if (sumAvailable < payout.getAmount()) {
                        throw new ResponseCodeException(400,
                                                        "You have insufficient funds in your Stripe account for this transfer. Your card balance is too low.  You can use the /v1/balance endpoint to view your Stripe balance (for more details, see stripe.com/docs/api#balance).",
                                                        "balance_insufficient",
                                                        "invalid_request_error",
                                                        null,
                                                        null);
                    }

                    payout.setBalanceTransaction(Utilities.randomIdWithPrefix("txn", 24));
                    // By registering this, it can be converted on the fly when expanded or fetched.
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
            return entities.values()
                           .stream()
                           .toList();
        } else {
            Set<String> payoutIdsForStripeAccount = stripeAccountToPayoutId.getOrDefault(stripeAccount, new HashSet<>());
            return entities.values()
                           .stream()
                           .filter(payout -> payoutIdsForStripeAccount.contains(payout.getId()))
                           .toList();
        }
    }

    @Override
    public void clear() {
        super.clear();
        stripeAccountToPayoutId.clear();
    }
}
