package com.sesame.oss.stripemock.util;

import com.stripe.model.Balance;
import com.stripe.model.BalanceTransaction;

import java.util.Collections;
import java.util.List;

public class BalanceUtilities {
    public static Balance createBalance(List<BalanceTransaction> balanceTransactions) {
        Balance balance = new Balance();
        balance.setObject("balance");
        balance.setLivemode(false);
        balance.setAvailable(createAvailable(balanceTransactions));
        return balance;
    }

    private static List<Balance.Available> createAvailable(List<BalanceTransaction> balanceTransactions) {
        // todo: this should really be one per account, and each account is in a currency, but we can deal with that later
        Balance.Available available = new Balance.Available();
        available.setCurrency("USD");
        available.setAmount(balanceTransactions.stream()
                                               .filter(txn -> txn.getStatus()
                                                                 .equals("available"))
                                               .mapToLong(BalanceTransaction::getAmount)
                                               .sum());
        available.setSourceTypes(new Balance.Available.SourceTypes()); // todo: populate this
        return Collections.singletonList(available);
    }
}
