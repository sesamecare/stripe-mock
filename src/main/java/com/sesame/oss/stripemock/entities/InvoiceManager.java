package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Invoice;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

class InvoiceManager extends AbstractEntityManager<Invoice> {
    protected InvoiceManager(Clock clock) {
        super(clock, Invoice.class, "in");
    }

    @Override
    protected Invoice initialize(Invoice entity, Map<String, Object> formData) throws ResponseCodeException {
        entity.setStatus("draft");
        return super.initialize(entity, formData);
    }

    @Override
    protected Invoice perform(Invoice existingEntity, Invoice updatedEntity, String operation, Map<String, Object> formData) throws ResponseCodeException {
        if (operation.equals("finalize")) {
            // todo: figure out what else we want to do here
            updatedEntity.setStatus("open");
        } else if (operation.equals(MAGIC_UPDATE_OPERATION)) {
            if (updatedEntity.getStatus()
                             .equals("open")) {
                // todo: sync with stripe's error message
                throw new ResponseCodeException(400, "Can't update a finalized invoice");
            }
        }
        return updatedEntity;
    }

    @Override
    public Optional<Invoice> delete(String id) throws ResponseCodeException {
        // todo: sync with the behavior of stripe
        Invoice invoice = entities.remove(id);
        if (invoice == null) {
            return Optional.empty();
        }
        if (!invoice.getStatus()
                    .equals("draft")) {
            throw new ResponseCodeException(400, "Cannot delete an invoice that is not in status 'draft'");
        }
        invoice.setDeleted(true);
        return Optional.of(invoice);
    }
}
