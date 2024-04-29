package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.QueryParameters;
import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

class TransferReversalManager extends AbstractEntityManager<TransferReversal> {
    private final StripeEntities stripeEntities;

    protected TransferReversalManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, TransferReversal.class, "trr", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public TransferReversal add(Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException {
        if (!parentEntityType.equals("transfers")) {
            throw new UnsupportedOperationException("Reversals can't be attached to things that are not transfers");
        }
        EntityManager<Transfer> transfersEntityManager = stripeEntities.getEntityManager(Transfer.class);
        Transfer parentTransfer = transfersEntityManager.get(parentEntityId, stripeAccount)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "transfers", parentEntityId));
        if (!formData.containsKey("amount")) {
            formData.put("amount", parentTransfer.getAmount());
            formData.put("currency", parentTransfer.getCurrency());
        }

        TransferReversal transferReversal = add(formData, stripeAccount);
        transferReversal.setTransfer(parentEntityId);

        parentTransfer.getReversals()
                      .getData()
                      .add(transferReversal);
        long totalAmountReversed = parentTransfer.getReversals()
                                                 .getData()
                                                 .stream()
                                                 .mapToLong(TransferReversal::getAmount)
                                                 .sum();
        parentTransfer.setReversed(Objects.equals(totalAmountReversed, parentTransfer.getAmount()));
        parentTransfer.setAmountReversed(totalAmountReversed);
        return transferReversal;
    }

    @Override
    protected TransferReversal initialize(TransferReversal transferReversal, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        transferReversal.setBalanceTransaction(Utilities.randomIdWithPrefix("txn", 24));
        // By registering this, it can be converted on the fly when expanded or fetched.
        BalanceTransactionManager balanceTransactionEntityManager = (BalanceTransactionManager) stripeEntities.getEntityManager(BalanceTransaction.class);
        balanceTransactionEntityManager.register(transferReversal.getBalanceTransaction(), transferReversal);
        return super.initialize(transferReversal, formData, stripeAccount);
    }

    @Override
    public List<TransferReversal> list(QueryParameters query, String stripeAccount) {
        return entities.values()
                       .stream()
                       .filter(transferReversal -> stripeAccount == null ||
                                                   stripeAccount.equals(getTransferOrThrow(stripeAccount, transferReversal).getDestination()))
                       .toList();
    }

    @Override
    public Optional<TransferReversal> get(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        assertParentConnectionIsValid(id, stripeAccount, parentEntityType, parentEntityId);
        return Optional.ofNullable(entities.get(id));
    }

    @Override
    public Optional<TransferReversal> update(String id, Map<String, Object> formData, String stripeAccount, String parentEntityType, String parentEntityId)
            throws ResponseCodeException {
        assertParentConnectionIsValid(id, stripeAccount, parentEntityType, parentEntityId);
        return update(id, formData, stripeAccount);
    }

    private void assertParentConnectionIsValid(String id, String stripeAccount, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("transfers")) {
            throw new UnsupportedOperationException("Transfer reversals can't be attached to things that are not transfers");
        }
        EntityManager<Transfer> transfersEntityManager = stripeEntities.getEntityManager(Transfer.class);
        Transfer parentTransfer = transfersEntityManager.get(parentEntityId, stripeAccount)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "transfers", parentEntityId));
        if (parentTransfer.getReversals()
                          .getData()
                          .stream()
                          .noneMatch(transferReversal -> transferReversal.getId()
                                                                         .equals(id))) {
            throw new ResponseCodeException(400, String.format("Reversal %s not connected to parent %s", id, parentEntityId));
        }
    }

    private Transfer getTransferOrThrow(String stripeAccount, TransferReversal transferReversal) {
        try {
            return stripeEntities.getEntityManager(Transfer.class)
                                 .get(transferReversal.getTransfer(), stripeAccount)
                                 .orElseThrow(() -> new AssertionError(String.format("Transfer reversal %s references transfer %s, which doesn't exist!",
                                                                                     transferReversal.getId(),
                                                                                     transferReversal.getTransfer())));
        } catch (ResponseCodeException e) {
            // This should never happen
            throw new AssertionError("Could not find transfer", e);
        }
    }

    @Override
    public String getNormalizedEntityName() {
        return "reversals";
    }
}
