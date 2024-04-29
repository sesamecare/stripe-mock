package com.sesame.oss.stripemock.util;

import com.stripe.model.Balance;
import com.stripe.model.BalanceTransaction;

import java.util.Collections;
import java.util.List;

public class BalanceUtilities {
    // todo: populate source types
    // todo: calculate things that aren't available
    // todo: one list item for each per account/currency
    public static Balance createBalance(List<BalanceTransaction> balanceTransactions, String stripeAccount) {
        Balance balance = new Balance();
        balance.setObject("balance");
        balance.setLivemode(false);
        balance.setAvailable(createAvailable(balanceTransactions));
        balance.setPending(createPending(balanceTransactions));
        balance.setInstantAvailable(createInstantAvailable(balanceTransactions));
        if (stripeAccount == null) {
            balance.setConnectReserved(createConnectReserved(balanceTransactions));
        }
        return balance;
    }

    private static List<Balance.ConnectReserved> createConnectReserved(List<BalanceTransaction> balanceTransactions) {
        Balance.ConnectReserved connectReserved = new Balance.ConnectReserved();
        connectReserved.setAmount(0L);
        connectReserved.setCurrency("USD");
        connectReserved.setSourceTypes(new Balance.ConnectReserved.SourceTypes());
        return Collections.singletonList(connectReserved);
    }

    private static List<Balance.InstantAvailable> createInstantAvailable(List<BalanceTransaction> balanceTransactions) {
        Balance.InstantAvailable instantAvailable = new Balance.InstantAvailable();
        instantAvailable.setAmount(0L);
        instantAvailable.setCurrency("USD");
        instantAvailable.setSourceTypes(new Balance.InstantAvailable.SourceTypes());
        return Collections.singletonList(instantAvailable);
    }

    private static List<Balance.Pending> createPending(List<BalanceTransaction> balanceTransactions) {
        Balance.Pending pending = new Balance.Pending();
        pending.setAmount(0L);
        pending.setCurrency("USD");
        pending.setSourceTypes(new Balance.Pending.SourceTypes());
        return Collections.singletonList(pending);
    }

    private static List<Balance.Available> createAvailable(List<BalanceTransaction> balanceTransactions) {
        Balance.Available available = new Balance.Available();
        available.setCurrency("USD");
        available.setAmount(sum(balanceTransactions, "available"));
        available.setSourceTypes(new Balance.Available.SourceTypes());
        return Collections.singletonList(available);
    }

    public static long sum(List<BalanceTransaction> balanceTransactions, String status) {
        return balanceTransactions.stream()
                                  .filter(txn -> txn.getStatus()
                                                    .equals(status))
                                  .mapToLong(BalanceTransaction::getAmount)
                                  .sum();
    }
}
