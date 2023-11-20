package com.sesame.oss.stripemock.entities;

import com.stripe.model.Transfer;
import com.stripe.model.TransferReversalCollection;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;

class TransferManager extends AbstractEntityManager<Transfer> {
    private final StripeEntities stripeEntities;

    protected TransferManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Transfer.class, "tr", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Transfer initialize(Transfer transfer, Map<String, Object> formData) {
        TransferReversalCollection reversals = new TransferReversalCollection();
        reversals.setData(new ArrayList<>());
        reversals.setUrl("/v1/transfers/" + transfer.getId() + "/reversals");
        transfer.setReversals(reversals);
        transfer.setReversed(false);
        return transfer;
    }
}
