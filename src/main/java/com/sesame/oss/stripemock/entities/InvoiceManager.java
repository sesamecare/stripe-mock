package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.InvoiceLineItemCollection;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

class InvoiceManager extends AbstractEntityManager<Invoice> {
    private final StripeEntities stripeEntities;

    protected InvoiceManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Invoice.class, "in", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Invoice initialize(Invoice invoice, Map<String, Object> formData) throws ResponseCodeException {
        invoice.setStatus("draft");
        invoice.setPaid(false);

        // These can be affected by inputs, but if they are not, these are their defaults
        if (invoice.getPaymentSettings() == null) {
            invoice.setPaymentSettings(new Invoice.PaymentSettings());
        }
        if (invoice.getAutoAdvance() == null) {
            invoice.setAutoAdvance(false);
        }
        if (invoice.getAutomaticTax() == null) {
            Invoice.AutomaticTax automaticTax = new Invoice.AutomaticTax();
            automaticTax.setEnabled(false);
            invoice.setAutomaticTax(automaticTax);
        }

        // These are always defaulted and are not affected by inputs
        Customer customer = stripeEntities.getEntityManager(Customer.class)
                                          .get(invoice.getCustomer())
                                          .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "customer", invoice.getCustomer()));
        invoice.setAccountCountry("US");
        invoice.setAccountName("Stripe-mock");
        invoice.setBillingReason("manual");
        invoice.setCollectionMethod("charge_automatically");
        invoice.setCustomerName(customer.getName());
        invoice.setAmountDue(0L);
        invoice.setAmountPaid(0L);
        invoice.setAmountRemaining(0L);
        invoice.setAmountShipping(0L);
        invoice.setAttempted(false);
        invoice.setAttemptCount(0L);
        invoice.setCustomerTaxIds(new ArrayList<>());
        invoice.setDefaultTaxRates(new ArrayList<>());
        invoice.setDiscounts(new ArrayList<>());
        invoice.setPeriodStart(invoice.getCreated());
        invoice.setPeriodEnd(invoice.getCreated());
        invoice.setPostPaymentCreditNotesAmount(0L);
        invoice.setPrePaymentCreditNotesAmount(0L);
        invoice.setStartingBalance(0L);
        invoice.setStatusTransitions(new Invoice.StatusTransitions());
        invoice.setTotal(0L);
        invoice.setTotalDiscountAmounts(new ArrayList<>());
        invoice.setTotalExcludingTax(0L);
        invoice.setTotalTaxAmounts(new ArrayList<>());

        InvoiceLineItemCollection lines = new InvoiceLineItemCollection();
        lines.setData(new ArrayList<>());
        lines.setUrl("/v1/invoices/" + invoice.getId() + "/lines");
        invoice.setLines(lines);

        return super.initialize(invoice, formData);
    }

    @Override
    protected Invoice perform(Invoice existingInvoice, Invoice updatedInvoice, String operation, Map<String, Object> formData) throws ResponseCodeException {
        if (operation.equals("finalize")) {
            updatedInvoice.setStatus("paid");
            updatedInvoice.setPaid(true);

            updatedInvoice.setAttempted(true);
            updatedInvoice.setEffectiveAt(Instant.now(clock)
                                                 .getEpochSecond());
            updatedInvoice.setEndingBalance(0L);
            // todo: hosted_invoice_url, invoice_pdf
            // "hosted_invoice_url": "https://invoice.stripe.com/i/acct_1D4ByEF2zu6DinIq/test_YWNjdF8xRDRCeUVGMnp1NkRpbklxLF9PeE1JeHNFSHVZTGd0MnNhcDk2akdFYUNlOFA5UUo5LDg5ODEzMjY40200Ghh1Nmex?s\u003dap",
            // "invoice_pdf": "https://pay.stripe.com/invoice/acct_1D4ByEF2zu6DinIq/test_YWNjdF8xRDRCeUVGMnp1NkRpbklxLF9PeE1JeHNFSHVZTGd0MnNhcDk2akdFYUNlOFA5UUo5LDg5ODEzMjY40200Ghh1Nmex/pdf?s\u003dap",
            updatedInvoice.setNumber("todo: what should this be? 666970EC-0001");
            updatedInvoice.getStatusTransitions()
                          .setFinalizedAt(Instant.now(clock)
                                                 .getEpochSecond());
            updatedInvoice.getStatusTransitions()
                          .setPaidAt(Instant.now(clock)
                                            .getEpochSecond());
            updatedInvoice.setSubtotal(0L);
            updatedInvoice.setSubtotalExcludingTax(0L);
        } else if (operation.equals(MAGIC_UPDATE_OPERATION)) {
            if (!updatedInvoice.getStatus()
                               .equals("draft")) {
                // todo: sync with stripe's error message
                throw new ResponseCodeException(400, "Can't update a finalized invoice");
            }
        }
        return updatedInvoice;
    }

    @Override
    public boolean canPerformOperation(String operation) {
        return operation.equals("finalize");
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
