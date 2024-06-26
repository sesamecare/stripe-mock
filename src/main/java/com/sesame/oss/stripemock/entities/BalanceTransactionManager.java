package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.*;
import com.stripe.net.ApiResource;

import java.time.Clock;
import java.util.*;

class BalanceTransactionManager extends AbstractEntityManager<BalanceTransaction> {
    private final Map<String, String> sourcesByBalanceTransactionId = new HashMap<>();

    BalanceTransactionManager(Clock clock, StripeEntities stripeEntities) {
        super(stripeEntities, clock, BalanceTransaction.class, "txn", 24);
    }

    @Override
    public BalanceTransaction add(Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        throw new IllegalStateException("Cannot explicitly create balance transactions");
    }

    @Override
    public Optional<BalanceTransaction> delete(String id) throws ResponseCodeException {
        throw new IllegalStateException("Cannot explicitly create balance transactions");
    }

    @Override
    public Optional<BalanceTransaction> update(String id, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        throw new IllegalStateException("Cannot explicitly create balance transactions");
    }

    @Override
    public Optional<BalanceTransaction> get(String id, String stripeAccount) throws ResponseCodeException {
        return Optional.ofNullable(sourcesByBalanceTransactionId.get(id))
                       .flatMap(stripeEntities::getEntityById)
                       .map(source -> BalanceTransactionMapper.toBalanceTransaction((BalanceTransactionSource) source, stripeAccount));
    }

    void register(String id, BalanceTransactionSource balanceTransactionSource) {
        sourcesByBalanceTransactionId.put(id, balanceTransactionSource.getId());
    }

    @Override
    public List<BalanceTransaction> list(QueryParameters query, String stripeAccount) throws ResponseCodeException {
        if (stripeAccount != null) {
            // This will throw if the account isn't valid.
            stripeEntities.getEntityManager(Account.class)
                          .get(stripeAccount, stripeAccount);
        }

        List<BalanceTransaction> balanceTransactions = new ArrayList<>();

        add(TransferReversal.class, balanceTransactions, query, stripeAccount);
        add(Transfer.class, balanceTransactions, query, stripeAccount);
        add(Payout.class, balanceTransactions, query, stripeAccount);
        if (stripeAccount == null) {
            add(Charge.class, balanceTransactions, query, null);
            add(Refund.class, balanceTransactions, query, null);
        }
        // Most recent first
        balanceTransactions.sort(Comparator.comparing(BalanceTransaction::getCreated)
                                           .reversed());
        return balanceTransactions;
    }

    private <T extends ApiResource & HasId & BalanceTransactionSource> void add(Class<T> clazz,
                                                                                List<BalanceTransaction> balanceTransactions,
                                                                                QueryParameters query,
                                                                                String stripeAccount) throws ResponseCodeException {
        for (T entity : stripeEntities.getEntityManager(clazz)
                                      .list(query, stripeAccount)) {
            balanceTransactions.add(BalanceTransactionMapper.toBalanceTransaction(entity, stripeAccount));
        }
    }

    @Override
    public boolean canPerformOperation(String operation) {
        return operation.equals("search");
    }
}
