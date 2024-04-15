package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.sesame.oss.stripemock.util.Utilities;
import com.stripe.model.*;
import com.stripe.net.ApiResource;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class SubscriptionManager extends AbstractEntityManager<Subscription> {
    private final StripeEntities stripeEntities;

    SubscriptionManager(Clock clock, StripeEntities stripeEntities) {
        super(clock, Subscription.class, "sub", 24);
        this.stripeEntities = stripeEntities;
    }

    @Override
    protected Subscription initialize(Subscription subscription, Map<String, Object> formData) throws ResponseCodeException {
        if (subscription.getCustomer() == null) {
            throw new ResponseCodeException(400, "Missing required param: customer.");
        }
        String id = subscription.getCustomer();
        stripeEntities.getEntityManager(Customer.class)
                      .get(id)
                      .orElseThrow(() -> ResponseCodeException.noSuchEntity(400, "customer", subscription.getCustomer()));

        Map<String, Object> invoiceParameters = new HashMap<>();
        invoiceParameters.put("subscription", subscription.getId());
        invoiceParameters.put("customer", subscription.getCustomer());
        EntityManager<Invoice> invoiceEntityManager = stripeEntities.getEntityManager(Invoice.class);
        Invoice firstInvoice = invoiceEntityManager.add(invoiceParameters);
        for (SubscriptionItem subscriptionItem : subscription.getItems()
                                                             .getData()) {
            subscriptionItem.setId(Utilities.randomIdWithPrefix("si", 24));
            subscriptionItem.setSubscription(subscription.getId());
            firstInvoice.getLines()
                        .getData()
                        .add(toInvoiceLineItem(subscriptionItem));
        }
        // invoices that are part of a subscription are automatically finalized, meaning that they can't change.
        // This moves them from 'draft' to 'open'
        firstInvoice = invoiceEntityManager.perform(firstInvoice.getId(), "finalize", new HashMap<>())
                                           .orElseThrow();


        // todo: should this be done automatically when creating the invoice, or can invoices be created without payment intents?
        Map<String, Object> paymentIntentFormData = new HashMap<>();
        paymentIntentFormData.put("amount",
                                  subscription.getItems()
                                              .getData()
                                              .stream()
                                              .mapToLong(subscriptionItem -> subscriptionItem.getPrice()
                                                                                             .getUnitAmount())
                                              .sum());
        paymentIntentFormData.put("currency",
                                  subscription.getItems()
                                              .getData()
                                              .stream()
                                              .map(subscriptionItem -> subscriptionItem.getPrice()
                                                                                       .getCurrency())
                                              .findAny()
                                              .orElseThrow());
        paymentIntentFormData.put("customer", subscription.getCustomer());
        PaymentIntent invoicePaymentIntent = stripeEntities.getEntityManager(PaymentIntent.class)
                                                           .add(paymentIntentFormData);
        firstInvoice.setPaymentIntent(invoicePaymentIntent.getId());
        invoicePaymentIntent.setInvoice(firstInvoice.getId());

        subscription.setStartDate(subscription.getCreated());
        subscription.setCancelAtPeriodEnd(false);
        subscription.setLatestInvoice(firstInvoice.getId());
        subscription.setStatus("incomplete");
        return super.initialize(subscription, formData);
    }

    private InvoiceLineItem toInvoiceLineItem(SubscriptionItem subscriptionItem) {
        String json = Utilities.PRODUCER_GSON.toJson(subscriptionItem);
        InvoiceLineItem invoiceLineItem = ApiResource.GSON.fromJson(json, InvoiceLineItem.class);
        invoiceLineItem.setObject("line_item");
        invoiceLineItem.setId(Utilities.randomIdWithPrefix("il_tmp", 24));

        // todo: should we set anything else?
        Price price = subscriptionItem.getPrice();
        invoiceLineItem.setCurrency(price.getCurrency());
        invoiceLineItem.setAmount(price.getUnitAmount());
        invoiceLineItem.setLivemode(false);
        invoiceLineItem.setSubscriptionItem(subscriptionItem.getId());
        invoiceLineItem.setSubscription(subscriptionItem.getSubscription());
        return invoiceLineItem;
    }

    @Override
    public Optional<Subscription> delete(String id) throws ResponseCodeException {
        Subscription subscription = entities.get(id);
        if (subscription == null) {
            return Optional.empty();
        }
        if (subscription.getStatus()
                        .equals("canceled")) {
            // todo: should we throw if we try to re-cancel?
            return Optional.of(subscription);
        }
        long nowInEpochSecond = Instant.now(clock)
                                       .getEpochSecond();
        subscription.setCanceledAt(nowInEpochSecond);
        subscription.setEndedAt(nowInEpochSecond);
        subscription.setStatus("canceled");
        return Optional.of(subscription);
    }

    @Override
    protected Subscription parse(Map<String, Object> formData) {
        Object items = formData.get("items");
        if (items instanceof Map itemsStripeCollection) {
            Object stripeCollectionData = itemsStripeCollection.get("data");
            if (stripeCollectionData instanceof Object[] stripeCollectionDataArray) {
                for (Object stripeCollectionDataItem : stripeCollectionDataArray) {
                    if (stripeCollectionDataItem instanceof Map itemMap) {
                        Object priceData = itemMap.remove("price_data");
                        if (priceData != null) {
                            itemMap.put("price", priceData);
                        }
                    }
                }
            }
        }
        return super.parse(formData);
    }
}
