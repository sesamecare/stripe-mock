package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.CustomerUpdateParams.InvoiceSettings;
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
                                                                .setName("stripe-mock test")
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
                                                                .setName("stripe-mock test")
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

        assertEquals("Missing required param: customer.",
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
                                                                .setName("stripe-mock test")
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

    @Test
    void shouldPayForSubscriptionPaymentIntentWithPaymentMethodStoredInInvoiceSettings() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .putMetadata("integration_test", "true")
                                                                .build());

        PaymentMethod paymentMethod = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                                    .putMetadata("integration_test", "true")
                                                                                    .setType(PaymentMethodCreateParams.Type.CARD)
                                                                                    .setCard(PaymentMethodCreateParams.Token.builder()
                                                                                                                            .setToken("tok_mastercard")
                                                                                                                            .build())
                                                                                    .build())
                                                   .attach(PaymentMethodAttachParams.builder()
                                                                                    .setCustomer(customer.getId())
                                                                                    .build());
        customer.update(CustomerUpdateParams.builder()
                                            .setInvoiceSettings(InvoiceSettings.builder()
                                                                               .setDefaultPaymentMethod(paymentMethod.getId())
                                                                               .build())
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
                                                            .addExpand("latest_invoice.payment_intent")
                                                            .build());


        // todo: test that this fails if the customer doesn't have the payment method attached BEFORE the subscription is created
        PaymentIntent subscriptionPaymentIntent = createdSubscription.getLatestInvoiceObject()
                                                                     .getPaymentIntentObject();
        System.out.println("before confirmation = " + subscriptionPaymentIntent);
        PaymentIntent confirmedPaymentIntent = subscriptionPaymentIntent.confirm();
        System.out.println("after confirmation = " + confirmedPaymentIntent);
        // todo: figure out what to assert on
        // stripe data:
        /*
before confirmation = <com.stripe.model.PaymentIntent@1456061400 id=pi_3O9VvxF2zu6DinIq0VoNZavG> JSON: {
  "amount": 1000,
  "amount_capturable": 0,
  "amount_details": {
    "tip": {
      "amount": null
    }
  },
  "amount_received": 0,
  "application": null,
  "application_fee_amount": null,
  "automatic_payment_methods": null,
  "canceled_at": null,
  "cancellation_reason": null,
  "capture_method": "automatic",
  "client_secret": "pi_3O9VvxF2zu6DinIq0VoNZavG_secret_oDz6Ni7a6iNJykLSX5iK6vdoH",
  "confirmation_method": "automatic",
  "created": 1699289153,
  "currency": "usd",
  "customer": "cus_OxQnFY7mOEHu73",
  "description": "Subscription creation",
  "id": "pi_3O9VvxF2zu6DinIq0VoNZavG",
  "invoice": "in_1O9VvxF2zu6DinIqzJZ51lht",
  "last_payment_error": null,
  "latest_charge": null,
  "livemode": false,
  "metadata": {},
  "next_action": null,
  "object": "payment_intent",
  "on_behalf_of": null,
  "payment_method": "pm_1O9VvvF2zu6DinIqKu785fiu",
  "payment_method_options": {
    "acss_debit": null,
    "affirm": null,
    "afterpay_clearpay": null,
    "alipay": null,
    "au_becs_debit": null,
    "bacs_debit": null,
    "bancontact": null,
    "blik": null,
    "boleto": null,
    "card": {
      "capture_method": null,
      "installments": null,
      "mandate_options": null,
      "network": null,
      "request_three_d_secure": "automatic",
      "setup_future_usage": null,
      "statement_descriptor_suffix_kana": null,
      "statement_descriptor_suffix_kanji": null
    },
    "card_present": null,
    "cashapp": {
      "capture_method": null,
      "setup_future_usage": null
    },
    "customer_balance": null,
    "eps": null,
    "fpx": null,
    "giropay": null,
    "grabpay": null,
    "ideal": null,
    "interac_present": null,
    "klarna": null,
    "konbini": null,
    "link": null,
    "oxxo": null,
    "p24": null,
    "paynow": null,
    "paypal": null,
    "pix": null,
    "promptpay": null,
    "sepa_debit": null,
    "sofort": null,
    "us_bank_account": null,
    "wechat_pay": null,
    "zip": null
  },
  "payment_method_types": [
    "card",
    "cashapp"
  ],
  "processing": null,
  "receipt_email": null,
  "review": null,
  "setup_future_usage": "off_session",
  "shipping": null,
  "source": null,
  "statement_descriptor": null,
  "statement_descriptor_suffix": null,
  "status": "requires_confirmation",
  "transfer_data": null,
  "transfer_group": null
}
after confirmation = <com.stripe.model.PaymentIntent@1661777060 id=pi_3O9VvxF2zu6DinIq0VoNZavG> JSON: {
  "amount": 1000,
  "amount_capturable": 0,
  "amount_details": {
    "tip": {
      "amount": null
    }
  },
  "amount_received": 1000,
  "application": null,
  "application_fee_amount": null,
  "automatic_payment_methods": null,
  "canceled_at": null,
  "cancellation_reason": null,
  "capture_method": "automatic",
  "client_secret": "pi_3O9VvxF2zu6DinIq0VoNZavG_secret_oDz6Ni7a6iNJykLSX5iK6vdoH",
  "confirmation_method": "automatic",
  "created": 1699289153,
  "currency": "usd",
  "customer": "cus_OxQnFY7mOEHu73",
  "description": "Subscription creation",
  "id": "pi_3O9VvxF2zu6DinIq0VoNZavG",
  "invoice": "in_1O9VvxF2zu6DinIqzJZ51lht",
  "last_payment_error": null,
  "latest_charge": "ch_3O9VvxF2zu6DinIq0NHBwBaw",
  "livemode": false,
  "metadata": {},
  "next_action": null,
  "object": "payment_intent",
  "on_behalf_of": null,
  "payment_method": "pm_1O9VvvF2zu6DinIqKu785fiu",
  "payment_method_options": {
    "acss_debit": null,
    "affirm": null,
    "afterpay_clearpay": null,
    "alipay": null,
    "au_becs_debit": null,
    "bacs_debit": null,
    "bancontact": null,
    "blik": null,
    "boleto": null,
    "card": {
      "capture_method": null,
      "installments": null,
      "mandate_options": null,
      "network": null,
      "request_three_d_secure": "automatic",
      "setup_future_usage": null,
      "statement_descriptor_suffix_kana": null,
      "statement_descriptor_suffix_kanji": null
    },
    "card_present": null,
    "cashapp": {
      "capture_method": null,
      "setup_future_usage": null
    },
    "customer_balance": null,
    "eps": null,
    "fpx": null,
    "giropay": null,
    "grabpay": null,
    "ideal": null,
    "interac_present": null,
    "klarna": null,
    "konbini": null,
    "link": null,
    "oxxo": null,
    "p24": null,
    "paynow": null,
    "paypal": null,
    "pix": null,
    "promptpay": null,
    "sepa_debit": null,
    "sofort": null,
    "us_bank_account": null,
    "wechat_pay": null,
    "zip": null
  },
  "payment_method_types": [
    "card",
    "cashapp"
  ],
  "processing": null,
  "receipt_email": null,
  "review": null,
  "setup_future_usage": "off_session",
  "shipping": null,
  "source": null,
  "statement_descriptor": null,
  "statement_descriptor_suffix": null,
  "status": "succeeded",
  "transfer_data": null,
  "transfer_group": null
}

         */


        // mock
/*
before confirmation = <com.stripe.model.PaymentIntent@1440621772 id=pi_2BP6488S0AAsQyiM6zTxByyp> JSON: {
  "amount": 1000,
  "amount_capturable": null,
  "amount_details": null,
  "amount_received": null,
  "application": null,
  "application_fee_amount": null,
  "automatic_payment_methods": null,
  "canceled_at": null,
  "cancellation_reason": null,
  "capture_method": "automatic",
  "client_secret": "pi_2BP6488S0AAsQyiM6zTxByyp_secret_IiXPz0bVBZqAnpMO62kzboSR4",
  "confirmation_method": "automatic",
  "created": 1699289285,
  "currency": "USD",
  "customer": "cus_bnryUeheml6JZPDsMrn5FnUj",
  "description": null,
  "id": "pi_2BP6488S0AAsQyiM6zTxByyp",
  "invoice": "in_ltKrxIYLuaGEjz13ggHx534N",
  "last_payment_error": null,
  "latest_charge": null,
  "livemode": false,
  "metadata": {},
  "next_action": null,
  "object": "payment_intent",
  "on_behalf_of": null,
  "payment_method": null,
  "payment_method_options": {
    "acss_debit": null,
    "affirm": null,
    "afterpay_clearpay": null,
    "alipay": null,
    "au_becs_debit": null,
    "bacs_debit": null,
    "bancontact": null,
    "blik": null,
    "boleto": null,
    "card": {
      "capture_method": null,
      "installments": null,
      "mandate_options": null,
      "network": null,
      "request_three_d_secure": "automatic",
      "setup_future_usage": null,
      "statement_descriptor_suffix_kana": null,
      "statement_descriptor_suffix_kanji": null
    },
    "card_present": null,
    "cashapp": null,
    "customer_balance": null,
    "eps": null,
    "fpx": null,
    "giropay": null,
    "grabpay": null,
    "ideal": null,
    "interac_present": null,
    "klarna": null,
    "konbini": null,
    "link": null,
    "oxxo": null,
    "p24": null,
    "paynow": null,
    "paypal": null,
    "pix": null,
    "promptpay": null,
    "sepa_debit": null,
    "sofort": null,
    "us_bank_account": null,
    "wechat_pay": null,
    "zip": null
  },
  "payment_method_types": [
    "card"
  ],
  "processing": null,
  "receipt_email": null,
  "review": null,
  "setup_future_usage": null,
  "shipping": null,
  "source": null,
  "statement_descriptor": null,
  "statement_descriptor_suffix": null,
  "status": "requires_payment_method",
  "transfer_data": null,
  "transfer_group": null
}
 */
        product.delete();
        customer.delete();
    }
}
