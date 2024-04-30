package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceItem;
import com.stripe.model.InvoiceLineItem;
import com.stripe.net.ApiResource;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

class InvoiceItemManager extends AbstractEntityManager<InvoiceItem> {
    protected InvoiceItemManager(Clock clock, StripeEntities stripeEntities) {
        super(stripeEntities, clock, InvoiceItem.class, "ii", 24);
    }

    @Override
    protected InvoiceItem initialize(InvoiceItem invoiceItem, Map<String, Object> formData, String stripeAccount) throws ResponseCodeException {
        String invoiceId = invoiceItem.getInvoice();
        if (invoiceId != null) {
            Invoice invoice = stripeEntities.getEntityManager(Invoice.class)
                                            .get(invoiceId, stripeAccount)
                                            .orElseThrow(() -> ResponseCodeException.noSuchEntity(404, "invoice", invoiceId));
            invoice.getLines()
                   .getData()
                   .add(convertToLineItem(invoiceItem));
            stripeEntities.bindChildToParentCollection(Invoice.class, invoiceId, "getLines", invoiceItem.getId());
        }
        return super.initialize(invoiceItem, formData, stripeAccount);
    }

    private InvoiceLineItem convertToLineItem(InvoiceItem invoiceItem) {
        String json = Utilities.PRODUCER_GSON.toJson(invoiceItem);
        InvoiceLineItem invoiceLineItem = ApiResource.GSON.fromJson(json, InvoiceLineItem.class);
        invoiceLineItem.setObject("line_item");
        invoiceLineItem.setId(Utilities.randomIdWithPrefix("il_tmp", 24));
        invoiceLineItem.setInvoiceItem(invoiceItem.getId());
        return invoiceLineItem;
    }

    @Override
    public String getNormalizedEntityName() {
        return "invoiceitems";
    }

    @Override
    public Optional<InvoiceItem> delete(String id) throws ResponseCodeException {
        // todo: sync with the behavior of stripe
        InvoiceItem invoiceItem = entities.remove(id);
        if (invoiceItem == null) {
            return Optional.empty();
        }
        invoiceItem.setDeleted(true);
        return Optional.of(invoiceItem);
    }
}
