package com.sesame.oss.stripemock.entities;

import com.stripe.model.Transfer;
import com.stripe.model.TransferReversalCollection;

import java.time.Clock;
import java.util.Map;

class TransferManager extends AbstractEntityManager<Transfer> {
    protected TransferManager(Clock clock) {
        super(clock, Transfer.class, "tr");
    }

    @Override
    protected Transfer initialize(Transfer entity, Map<String, Object> formData) {
        TransferReversalCollection reversals = new TransferReversalCollection();
        reversals.setUrl("/v1/transfers/" + entity.getId() + "/reversals");
        entity.setReversals(reversals);
        entity.setReversed(false);
        return entity;
    }
}
