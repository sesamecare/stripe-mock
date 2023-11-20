package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Transfer;
import com.stripe.model.TransferReversal;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

class TransferReversalManager extends AbstractEntityManager<TransferReversal> {
    private final StripeEntities stripeEntities;

    protected TransferReversalManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, TransferReversal.class, "trr", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    public TransferReversal add(Map<String, Object> formData, String parentEntityType, String parentEntityId) throws ResponseCodeException {
        if (!parentEntityType.equals("transfers")) {
            throw new UnsupportedOperationException("Reversals can't be attached to things that are not transfers");
        }
        EntityManager<Transfer> transfersEntityManager = stripeEntities.getEntityManager(Transfer.class);
        Transfer parentTransfer = transfersEntityManager.get(parentEntityId)
                                                        .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "transfers", parentEntityId));
        if (!formData.containsKey("amount")) {
            formData.put("amount", parentTransfer.getAmount());
        }

        TransferReversal transferReversal = add(formData);
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
    public String getNormalizedEntityName() {
        return "reversals";
    }
}
