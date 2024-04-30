package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversalCollection;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class TransferManager extends AbstractEntityManager<Transfer> {
    protected TransferManager(Clock clock, StripeEntities stripeEntities) {
        super(stripeEntities, clock, Transfer.class, "tr", 24);
    }

    @Override
    protected Transfer initialize(Transfer transfer, Map<String, Object> formData, String stripeAccount) {
        transfer.setAmountReversed(0L);

        TransferReversalCollection reversals = new TransferReversalCollection();
        reversals.setObject("list");
        reversals.setHasMore(false);
        reversals.setData(new ArrayList<>());
        reversals.setUrl("/v1/transfers/" + transfer.getId() + "/reversals");
        transfer.setReversals(reversals);
        transfer.setReversed(false);
        transfer.setBalanceTransaction(Utilities.randomIdWithPrefix("txn", 24));
        // By registering this, it can be converted on the fly when expanded or fetched.
        BalanceTransactionManager balanceTransactionEntityManager = (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
        balanceTransactionEntityManager.register(transfer.getBalanceTransaction(), transfer);
        return transfer;
    }

    @Override
    protected void validate(Transfer transfer) throws ResponseCodeException {
        if (transfer.getDestination() == null ||
            transfer.getDestination()
                    .isBlank()) {
            // todo: also verify that the account matching the destination actually exists
            throw new ResponseCodeException(400, "Missing required param: destination.", "parameter_missing", "invalid_request_error", null, "destination");
        }
    }

    @Override
    public List<Transfer> list(QueryParameters query, String stripeAccount) {
        return entities.values()
                       .stream()
                       .filter(transfer -> stripeAccount == null || stripeAccount.equals(transfer.getDestination()))
                       .toList();
    }
}
