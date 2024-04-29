package com.sesame.oss.stripemock;

import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.param.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.sesame.oss.stripemock.AccountTest.defaultCreationParameters;
import static com.sesame.oss.stripemock.AccountTest.defaultCreationParametersBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdOverrideTest extends AbstractStripeMockTest {
    @BeforeAll
    static void skipIfMockIsDisabled() {
        Assumptions.assumeFalse(StripeMock.isDisabled());
    }

    @Test
    void shouldSupportOverridingAccountId() throws StripeException {
        Account createdAccount = //
                Account.create(defaultCreationParametersBuilder("Stripe-mock test company name").putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "acct_abc123")
                                                                               .build());
        assertEquals("acct_abc123", createdAccount.getId());
    }

    @Test
    void shouldSupportOverridingCustomerId() throws StripeException {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "cus_abc123")
                                                                       .build());
        assertEquals("cus_abc123", createdCustomer.getId());
    }

    @Test
    void shouldSupportOverridingInvoiceId() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .build());
        Invoice invoice = Invoice.create(InvoiceCreateParams.builder()
                                                            .setCurrency("usd")
                                                            .setCustomer(customer.getId())
                                                            .setDescription("this is a stripe-mock test invoice")
                                                            .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "in_abc123")
                                                            .build());

        assertEquals("in_abc123", invoice.getId());
    }

    @Test
    void shouldSupportOverridingPaymentIntentId() throws StripeException {
        PaymentIntent paymentIntent = //
                PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                              .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "pi_abc123")
                                                              .addPaymentMethodType("card")
                                                              .setAmount(10_000L)
                                                              .setCurrency("USD")
                                                              .build());
        assertEquals("pi_abc123", paymentIntent.getId());
    }

    @Test
    void shouldSupportOverridingPaymentMethodId() throws StripeException {
        PaymentMethod createdPaymentMethod = //
                PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                              .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "pm_abc123")
                                                              .setType(PaymentMethodCreateParams.Type.CARD)
                                                              .setCard(PaymentMethodCreateParams.Token.builder()
                                                                                                      .setToken("tok_mastercard")
                                                                                                      .build())
                                                              .build());
        assertEquals("pm_abc123", createdPaymentMethod.getId());
    }

    @Test
    void shouldSupportOverridingProductId() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .setDescription("description 1")
                                                            .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "prod_abc123")
                                                            .build());

        assertEquals("prod_abc123", product.getId());
    }

    @Test
    void shouldSupportOverridingRefundId() throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.create(PaymentIntentCreateParams.builder()
                                                                                    .setAmount(10_00L)
                                                                                    .setCurrency("usd")
                                                                                    .build());
        paymentIntent.confirm(PaymentIntentConfirmParams.builder()
                                                        .setPaymentMethod("pm_card_mastercard")
                                                        .build());
        Refund createdRefund = //
                Refund.create(RefundCreateParams.builder()
                                                .setPaymentIntent(paymentIntent.getId())
                                                .setAmount(5_00L)
                                                .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "re_abc123")
                                                .build());
        assertEquals("re_abc123", createdRefund.getId());
    }

    @Test
    void shouldSupportOverridingSetupIntent() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .build());
        SetupIntent createdSetupIntent = //
                SetupIntent.create(SetupIntentCreateParams.builder()
                                                          .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "seti_abc123")
                                                          .setCustomer(customer.getId())
                                                          .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                                                          .build());
        assertEquals("seti_abc123", createdSetupIntent.getId());
    }

    @Test
    void shouldSupportOverridingSubscriptionId() throws StripeException {
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .putMetadata("integration_test", "true")
                                                            .build());
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .build());


        SubscriptionCreateParams.Item.PriceData.Recurring recurring = SubscriptionCreateParams.Item.PriceData.Recurring.builder()
                                                                                                                       .setInterval(SubscriptionCreateParams.Item.PriceData.Recurring.Interval.MONTH)
                                                                                                                       .setIntervalCount(1L)
                                                                                                                       .build();
        SubscriptionCreateParams.Item.PriceData priceData = SubscriptionCreateParams.Item.PriceData.builder()
                                                                                                   .setCurrency("USD")
                                                                                                   .setProduct(product.getId())
                                                                                                   .setRecurring(recurring)
                                                                                                   .setUnitAmount(10_00L)
                                                                                                   .build();
        Subscription subscription = Subscription.create(SubscriptionCreateParams.builder()
                                                                                .setCustomer(customer.getId())
                                                                                .addItem(SubscriptionCreateParams.Item.builder()
                                                                                                                      .setPriceData(priceData)
                                                                                                                      .build())
                                                                                .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "sub_abc123")
                                                                                .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                                                                                .build());
        assertEquals("sub_abc123", subscription.getId());
    }

    @Test
    void shouldSupportOverridingTransferId() throws StripeException {
        Account account = Account.create(defaultCreationParameters("Stripe-mock test company name"));
        Transfer createdTransfer = //
                Transfer.create(TransferCreateParams.builder()
                                                    .setAmount(9_000L)
                                                    .setCurrency("usd")
                                                    .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "tr_abc123")
                                                    .setTransferGroup("my transfer group")
                                                    .setSourceType(TransferCreateParams.SourceType.CARD)
                                                    .setDescription("my description")
                                                    .setDestination(account.getId())
                                                    .build());


        assertEquals("tr_abc123", createdTransfer.getId());

        TransferReversal transferReversal = createdTransfer.getReversals()
                                                           .create(TransferReversalCollectionCreateParams.builder()
                                                                                                         .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING,
                                                                                                                      "trr_abc123")
                                                                                                         .build());

        assertEquals("trr_abc123", transferReversal.getId());
    }
}
