package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.SubscriptionCreateParams.CollectionMethod;
import com.stripe.param.SubscriptionCreateParams.Item;
import com.stripe.param.SubscriptionCreateParams.Item.PriceData;
import com.stripe.param.SubscriptionCreateParams.Item.PriceData.Recurring;
import com.stripe.param.SubscriptionCreateParams.PaymentBehavior;
import com.stripe.param.SubscriptionCreateParams.PaymentSettings;
import com.stripe.param.SubscriptionCreateParams.PaymentSettings.PaymentMethodOptions;
import com.stripe.param.SubscriptionCreateParams.PaymentSettings.PaymentMethodOptions.Card;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SubscriptionTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());


        Recurring recurring = Recurring.builder()
                                       .setInterval(Recurring.Interval.MONTH)
                                       .setIntervalCount(1L)
                                       .build();
        PriceData priceData = PriceData.builder()
                                       .setCurrency("USD")
                                       .setProduct(product.getId())
                                       .setRecurring(recurring)
                                       .setUnitAmount(10_00L)
                                       .build();
        SubscriptionCreateParams input = SubscriptionCreateParams.builder()
                                                                 .setCustomer(customer.getId())
                                                                 .addItem(Item.builder()
                                                                              .setPriceData(priceData)
                                                                              .build())
                                                                 .putMetadata("integration_test", "true")
                                                                 .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                                 .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Subscription pi1 = Subscription.create(input, options);
        Subscription pi2 = Subscription.create(input, options);
        assertEquals(pi1, pi2);
        customer.delete();
        product.delete();

    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());

        String idempotencyKey = String.valueOf(Math.random());

        Recurring recurring = Recurring.builder()
                                       .setInterval(Recurring.Interval.MONTH)
                                       .setIntervalCount(1L)
                                       .build();
        PriceData priceData = PriceData.builder()
                                       .setCurrency("USD")
                                       .setProduct(product.getId())
                                       .setRecurring(recurring)
                                       .setUnitAmount(10_00L)
                                       .build();

        Subscription.create(SubscriptionCreateParams.builder()
                                                    .setCustomer(customer.getId())
                                                    .addItem(Item.builder()
                                                                 .setPriceData(priceData)
                                                                 .build())
                                                    .putMetadata("integration_test", "true")
                                                    .setDescription("description 1")
                                                    .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                    .build(),
                            RequestOptions.builder()
                                          .setIdempotencyKey(idempotencyKey)
                                          .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Subscription.create(SubscriptionCreateParams.builder()
                                                                                                                   .setCustomer(customer.getId())
                                                                                                                   .addItem(Item.builder()
                                                                                                                                .setPriceData(priceData)
                                                                                                                                .build())
                                                                                                                   .putMetadata("integration_test", "true")
                                                                                                                   .setDescription("description 2")
                                                                                                                   .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                                                                                   .build(),
                                                                                           RequestOptions.builder()
                                                                                                         .setIdempotencyKey(idempotencyKey)
                                                                                                         .build()));
        assertEquals(String.format(
                             "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request.",
                             idempotencyKey),
                     idempotencyException.getStripeError()
                                         .getMessage());
        customer.delete();
        product.delete();
    }

    @Test
    void shouldGet404OnMissingSubscription() {
        InvalidRequestException noSuchSubscription = assertThrows(InvalidRequestException.class, () -> Subscription.retrieve("sub_nope"));
        assertEquals("No such subscription: 'sub_nope'",
                     noSuchSubscription.getStripeError()
                                       .getMessage());
    }

    @Test
    void shouldNotCreateSubscriptionForNonexistentCustomer() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());

        Recurring recurring = Recurring.builder()
                                       .setInterval(Recurring.Interval.MONTH)
                                       .setIntervalCount(1L)
                                       .build();
        PriceData priceData = PriceData.builder()
                                       .setCurrency("USD")
                                       .setProduct(product.getId())
                                       .setRecurring(recurring)
                                       .setUnitAmount(10_00L)
                                       .build();

        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class,
                                                                       () -> Subscription.create(SubscriptionCreateParams.builder()
                                                                                                                         .putMetadata("integration_test",
                                                                                                                                      "true")
                                                                                                                         .addItem(Item.builder()
                                                                                                                                      .setPriceData(priceData)
                                                                                                                                      .build())
                                                                                                                         .setCustomer("cus_nope")
                                                                                                                         .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                                                                                         .setCollectionMethod(CollectionMethod.CHARGE_AUTOMATICALLY)
                                                                                                                         .build()));

        assertEquals("No such customer: 'cus_nope'",
                     invalidRequestException.getStripeError()
                                            .getMessage());

        product.delete();
    }

    @Test
    void shouldNotCreateSubscriptionWithoutCustomer() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());

        Recurring recurring = Recurring.builder()
                                       .setInterval(Recurring.Interval.MONTH)
                                       .setIntervalCount(1L)
                                       .build();
        PriceData priceData = PriceData.builder()
                                       .setCurrency("USD")
                                       .setProduct(product.getId())
                                       .setRecurring(recurring)
                                       .setUnitAmount(10_00L)
                                       .build();

        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class,
                                                                       () -> Subscription.create(SubscriptionCreateParams.builder()
                                                                                                                         .putMetadata("integration_test",
                                                                                                                                      "true")
                                                                                                                         .addItem(Item.builder()
                                                                                                                                      .setPriceData(priceData)
                                                                                                                                      .build())
                                                                                                                         .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                                                                                         .setCollectionMethod(CollectionMethod.CHARGE_AUTOMATICALLY)
                                                                                                                         .build()));

        assertEquals("Must provide a customer",
                     invalidRequestException.getStripeError()
                                            .getMessage());
    }

    @Test
    void testSubscription() throws Exception {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());

        Recurring recurring = Recurring.builder()
                                       .setInterval(Recurring.Interval.MONTH)
                                       .setIntervalCount(1L)
                                       .build();
        PriceData priceData = PriceData.builder()
                                       .setCurrency("USD")
                                       .setProduct(product.getId())
                                       .setRecurring(recurring)
                                       .setUnitAmount(10_00L)
                                       .build();

        Subscription createdSubscription = //
                Subscription.create(SubscriptionCreateParams.builder()
                                                            .putMetadata("integration_test", "true")
                                                            .addItem(Item.builder()
                                                                         .setPriceData(priceData)
                                                                         .build())
                                                            .setCustomer(customer.getId())
                                                            .setPaymentBehavior(PaymentBehavior.DEFAULT_INCOMPLETE)
                                                            .setCollectionMethod(CollectionMethod.CHARGE_AUTOMATICALLY)
                                                            .setPaymentSettings(PaymentSettings.builder()
                                                                                               .setPaymentMethodOptions(PaymentMethodOptions.builder()
                                                                                                                                            .setCard(Card.builder()
                                                                                                                                                         .setNetwork(
                                                                                                                                                                 Card.Network.AMEX)
                                                                                                                                                         .build())
                                                                                                                                            .build())
                                                                                               .build())
                                                            .addExpand("latest_invoice.payment_intent")
                                                            .build());

        Subscription retrievedSubscription = Subscription.retrieve(createdSubscription.getId());
        assertEquals(createdSubscription, retrievedSubscription);

        assertNotNull(createdSubscription.getLatestInvoice());
        assertNotNull(createdSubscription.getLatestInvoiceObject()
                                         .getPaymentIntentObject());

        Invoice subscriptionInvoice = Invoice.retrieve(createdSubscription.getLatestInvoice());
        assertNotNull(subscriptionInvoice.getPaymentIntent());
        assertNull(subscriptionInvoice.getPaymentIntentObject());

        InvalidRequestException invalidExpandPath = assertThrows(InvalidRequestException.class,
                                                                 () -> Invoice.retrieve(createdSubscription.getLatestInvoice(),
                                                                                        InvoiceRetrieveParams.builder()
                                                                                                             .addExpand("payment_intent.b.c")
                                                                                                             .build(),
                                                                                        RequestOptions.builder()
                                                                                                      .build()));
        assertEquals("This property cannot be expanded (payment_intent.b).",
                     invalidExpandPath.getStripeError()
                                      .getMessage());

        Invoice subscriptionInvoiceExpanded = Invoice.retrieve(createdSubscription.getLatestInvoice(),
                                                               InvoiceRetrieveParams.builder()
                                                                                    .addExpand("payment_intent")
                                                                                    .build(),
                                                               RequestOptions.builder()
                                                                             .build());
        assertNotNull(subscriptionInvoiceExpanded.getPaymentIntent());
        assertNotNull(subscriptionInvoiceExpanded.getPaymentIntentObject());

        PaymentIntent succeededSubscriptionPaymentIntent = subscriptionInvoiceExpanded.getPaymentIntentObject()
                                                                                      .confirm(PaymentIntentConfirmParams.builder()
                                                                                                                         .setPaymentMethod("pm_card_mastercard")
                                                                                                                         .addExpand("invoice.subscription")
                                                                                                                         .build());
        assertEquals("succeeded", succeededSubscriptionPaymentIntent.getStatus());
        assertEquals("paid",
                     succeededSubscriptionPaymentIntent.getInvoiceObject()
                                                       .getStatus());
        assertEquals("active",
                     succeededSubscriptionPaymentIntent.getInvoiceObject()
                                                       .getSubscriptionObject()
                                                       .getStatus());


        Subscription updatedSubscription = //
                retrievedSubscription.update(SubscriptionUpdateParams.builder()
                                                                     .setCancelAtPeriodEnd(true)
                                                                     .build());

        Subscription retrievedUpdatedSubscription = Subscription.retrieve(createdSubscription.getId());
        assertEquals(updatedSubscription, retrievedUpdatedSubscription);

        Subscription canceledSubscription = retrievedSubscription.cancel();
        assertNotNull(canceledSubscription.getCanceledAt());
        assertNotNull(canceledSubscription.getEndedAt());
        assertEquals("canceled", canceledSubscription.getStatus());

        Subscription retrievedCanceledSubscription = Subscription.retrieve(retrievedSubscription.getId());
        assertNotNull(retrievedCanceledSubscription.getCanceledAt());
        assertNotNull(retrievedCanceledSubscription.getEndedAt());
        assertEquals("canceled", retrievedCanceledSubscription.getStatus());

        customer.delete();
        product.delete();
        // todo: also test things like Subscription.resume(), .cancel(), .deleteDiscount().
    }
}
