package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.model.PaymentMethodCollection;
import com.stripe.net.RequestOptions;
import com.stripe.param.*;
import com.stripe.param.PaymentMethodCreateParams.Token;
import com.stripe.param.PaymentMethodCreateParams.Type;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class PaymentMethodTest extends AbstractStripeMockTest {


    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        PaymentMethodCreateParams input = PaymentMethodCreateParams.builder()
                                                                   .putMetadata("integration_test", "true")
                                                                   .setType(Type.CARD)
                                                                   .setCard(Token.builder()
                                                                                 .setToken("tok_mastercard")
                                                                                 .build())
                                                                   .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        PaymentMethod pm1 = PaymentMethod.create(input, options);
        PaymentMethod pm2 = PaymentMethod.create(input, options);
        assertEquals(pm1, pm2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        String idempotencyKey = String.valueOf(Math.random());
        PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                      .putMetadata("integration_test", "true")
                                                      .setType(Type.CARD)
                                                      .setCard(Token.builder()
                                                                    .setToken("tok_mastercard")
                                                                    .build())
                                                      .build(),
                             RequestOptions.builder()
                                           .setIdempotencyKey(idempotencyKey)
                                           .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                                                                     .putMetadata("integration_test", "true")
                                                                                                                     .setType(Type.CARD)
                                                                                                                     .setCard(Token.builder()
                                                                                                                                   .setToken("tok_visa")
                                                                                                                                   .build())
                                                                                                                     .build(),
                                                                                            RequestOptions.builder()
                                                                                                          .setIdempotencyKey(idempotencyKey)
                                                                                                          .build()));
        assertEquals(String.format(
                             "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request.",
                             idempotencyKey),
                     idempotencyException.getStripeError()
                                         .getMessage());
    }

    // todo: test without a customer: In order to share a PaymentMethod, you must pass a connected account ID by using an OAuth key or the Stripe-Account header.
    // This happens if you try to do this:
    // PaymentMethod.create(PaymentMethodCreateParams.builder().setPaymentMethod("tok_amex").build())

    // todo: test: com.stripe.exception.InvalidRequestException: You cannot attach a PaymentMethod to a Customer during PaymentMethod creation. Please instead create the PaymentMethod and then attach it using the attachment method of the PaymentMethods API.


    @Test
    void testPaymentMethod() throws Exception {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .build());
        PaymentMethod createdPaymentMethod = //
                PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                              .putMetadata("integration_test", "true")
                                                              .setType(Type.CARD)
                                                              .setCard(Token.builder()
                                                                            .setToken("tok_mastercard")
                                                                            .build())
                                                              .build());

        PaymentMethod withAttachedCustomer = createdPaymentMethod.attach(PaymentMethodAttachParams.builder()
                                                                                                  .setCustomer(customer.getId())
                                                                                                  .build());

        PaymentMethod retrievedPaymentMethod = PaymentMethod.retrieve(createdPaymentMethod.getId());
        assertEquals(withAttachedCustomer, retrievedPaymentMethod);

        PaymentMethod updatedPaymentMethod = //
                retrievedPaymentMethod.update(PaymentMethodUpdateParams.builder()
                                                                       .putMetadata("x", "y")
                                                                       .build());

        PaymentMethod retrievedUpdatedPaymentMethod = PaymentMethod.retrieve(createdPaymentMethod.getId());
        assertEquals(updatedPaymentMethod, retrievedUpdatedPaymentMethod);
    }

    @Test
    void shouldSetDefaultPaymentMethodForCustomerAutomatically() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Stripe-mock test")
                                                                .build());
        PaymentMethod pm = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                         .putMetadata("integration_test", "true")
                                                                         .setType(PaymentMethodCreateParams.Type.CARD)
                                                                         .setCard(PaymentMethodCreateParams.Token.builder()
                                                                                                                 .setToken("tok_mastercard")
                                                                                                                 .build())
                                                                         .build())
                                        .attach(PaymentMethodAttachParams.builder()
                                                                         .setCustomer(customer.getId())
                                                                         .build());
        assertEquals(pm.getId(),
                     Customer.retrieve(customer.getId())
                             .getInvoiceSettings()
                             .getDefaultPaymentMethod());
    }

    @Test
    void shouldListEmptyPaymentMethodsForCustomer() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        PaymentMethodCollection paymentMethods = PaymentMethod.list(PaymentMethodListParams.builder()
                                                                                           .setCustomer(customer.getId())
                                                                                           .setType(PaymentMethodListParams.Type.CARD)
                                                                                           .build());
        assertEquals(0,
                     paymentMethods.getData()
                                   .size());
    }

    @Test
    void shouldListPaymentMethodsForCustomer() throws StripeException {
        Customer c1 = Customer.create(CustomerCreateParams.builder()
                                                          .setName("Mike Smith")
                                                          .build());
        Customer c2 = Customer.create(CustomerCreateParams.builder()
                                                          .setName("Tim Jones")
                                                          .build());

        PaymentMethod pm1 = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                          .putMetadata("integration_test", "true")
                                                                          .setType(Type.CARD)
                                                                          .setCard(Token.builder()
                                                                                        .setToken("tok_mastercard")
                                                                                        .build())
                                                                          .build());

        pm1.attach(PaymentMethodAttachParams.builder()
                                            .setCustomer(c1.getId())
                                            .build());

        PaymentMethod pm2 = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                          /*
                                                                          .setBillingDetails(BillingDetails.builder()
                                                                                                           .setName("Mike Smith")
                                                                                                           .setAddress(Address.builder()
                                                                                                                              .setCountry("US")
                                                                                                                              .setCity("New York")
                                                                                                                              .setLine1("1 Main Street")
                                                                                                                              .setState("NY")
                                                                                                                              .setPostalCode("12345")
                                                                                                                              .build())
                                                                                                           .setEmail("mike.smith@example.com")
                                                                                                           .build())
                                                                          */

                                                                          .setType(Type.PAYPAL)
                                                                          .setPaypal(PaymentMethodCreateParams.Paypal.builder()
                                                                                                                     .build())
                                                                          .build());
        pm2.attach(PaymentMethodAttachParams.builder()
                                            .setCustomer(c1.getId())
                                            .build());

        PaymentMethod pm3 = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                          .putMetadata("integration_test", "true")
                                                                          .setType(Type.CARD)
                                                                          .setCard(Token.builder()
                                                                                        .setToken("tok_visa")
                                                                                        .build())
                                                                          .build());
        pm3.attach(PaymentMethodAttachParams.builder()
                                            .setCustomer(c2.getId())
                                            .build());

        PaymentMethodCollection paymentMethods = PaymentMethod.list(PaymentMethodListParams.builder()
                                                                                           .setCustomer(c1.getId())
                                                                                           .setType(PaymentMethodListParams.Type.CARD)
                                                                                           .build());
        assertEquals(1,
                     paymentMethods.getData()
                                   .size());
        assertEquals(pm1.getId(),
                     paymentMethods.getData()
                                   .get(0)
                                   .getId());

        PaymentMethod firstFoundPmAutoPaging = paymentMethods.autoPagingIterable()
                                                             .iterator()
                                                             .next();
        assertEquals(pm1.getId(), firstFoundPmAutoPaging.getId());

    }

    @Test
    void shouldAutomaticallySetCardData() throws StripeException {
        Customer c1 = Customer.create(CustomerCreateParams.builder()
                                                          .setName("Mike Smith")
                                                          .build());

        PaymentMethod pm1 = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                          .putMetadata("integration_test", "true")
                                                                          .setType(Type.CARD)
                                                                          .setCard(Token.builder()
                                                                                        .setToken("tok_mastercard")
                                                                                        .build())
                                                                          .build());

        PaymentMethod.Card mc = pm1.getCard();
        assertNotNull(mc);
        assertEquals("mastercard", mc.getBrand());
        assertEquals("US", mc.getCountry());
        assertEquals("credit", mc.getFunding());
        assertEquals(LocalDate.now(StripeMock.getClock())
                              .getMonth()
                              .getValue(), mc.getExpMonth());
        assertEquals(LocalDate.now(StripeMock.getClock())
                              .getYear() + 1, mc.getExpYear());

        PaymentMethod pm2 = PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                                          .putMetadata("integration_test", "true")
                                                                          .setType(Type.CARD)
                                                                          .setCard(Token.builder()
                                                                                        .setToken("tok_amex")
                                                                                        .build())
                                                                          .build());

        PaymentMethod.Card amex = pm2.getCard();
        assertNotNull(amex);
        assertEquals("amex", amex.getBrand());
        assertEquals("US", amex.getCountry());
        assertEquals("credit", amex.getFunding());
        assertEquals(LocalDate.now(StripeMock.getClock())
                              .getMonth()
                              .getValue(), amex.getExpMonth());
        assertEquals(LocalDate.now(StripeMock.getClock())
                              .getYear() + 1, amex.getExpYear());

        c1.delete();
    }

    //@Test
    void shouldDetachPaymentMethodFromCustomer() {
        // todo: implement me!
        fail("Implement me!");
    }
}
