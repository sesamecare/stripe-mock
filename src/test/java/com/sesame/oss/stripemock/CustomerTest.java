package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentMethod;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.CustomerUpdateParams.Address;
import com.stripe.param.PaymentMethodAttachParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.common.EmptyParam;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        CustomerCreateParams input = CustomerCreateParams.builder()
                                                         .setName("stripe-mock test")
                                                         .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Customer c1 = Customer.create(input, options);
        Customer c2 = Customer.create(input, options);
        assertEquals(c1, c2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        String idempotencyKey = String.valueOf(Math.random());
        Customer.create(CustomerCreateParams.builder()
                                            .setName("stripe-mock test")
                                            .build(),
                        RequestOptions.builder()
                                      .setIdempotencyKey(idempotencyKey)
                                      .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Customer.create(CustomerCreateParams.builder()
                                                                                                           .setName("Tim Jones")
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

    @Test
    void shouldSupportEmptyMetadataValue() throws StripeException {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .putMetadata("entity_type", null)
                                                                       .putMetadata("integration_test", "true")
                                                                       .setName("stripe-mock test")
                                                                       .build());
        assertNull(createdCustomer.getMetadata()
                                  .get("entity_type"));
    }


    @Test
    void testCustomer() throws Exception {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .setPhone("0000")
                                                                       .putMetadata("integration_test", "true")
                                                                       .setName("stripe-mock test")
                                                                       .setBalance(100_000_00L)
                                                                       .setValidate(false)
                                                                       .setAddress(CustomerCreateParams.Address.builder()
                                                                                                               .setCity("Stockholm")
                                                                                                               .setCountry("Sweden")
                                                                                                               .build())
                                                                       .build());
        assertEquals("0000", createdCustomer.getPhone());
        assertEquals("stripe-mock test", createdCustomer.getName());
        assertEquals("Stockholm",
                     createdCustomer.getAddress()
                                    .getCity());

        Customer retrievedCustomer = Customer.retrieve(createdCustomer.getId());
        assertEquals(createdCustomer, retrievedCustomer);

        Customer updatedCustomer = retrievedCustomer.update(CustomerUpdateParams.builder()
                                                                                .setName("Tim Jones")
                                                                                .setPhone("111-222-333")
                                                                                .setAddress(Address.builder()
                                                                                                   .setCity("Berlin")
                                                                                                   .setCountry("Germany")
                                                                                                   .build())
                                                                                .build());

        assertEquals("Tim Jones", updatedCustomer.getName());
        assertEquals("Berlin",
                     updatedCustomer.getAddress()
                                    .getCity());

        Customer retrievedUpdatedCustomer = Customer.retrieve(createdCustomer.getId());
        assertEquals(updatedCustomer, retrievedUpdatedCustomer);
    }

    @Test
    void shouldDeleteCustomer() throws StripeException {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .setName("stripe-mock test")
                                                                       .build());
        Customer retrievedCustomer1 = Customer.retrieve(createdCustomer.getId());
        assertEquals(createdCustomer.getId(), retrievedCustomer1.getId());
        Customer deletedCustomer = retrievedCustomer1.delete();
        assertTrue(deletedCustomer.getDeleted());
        // We can still fetch it, but the flag remains deleted
        assertTrue(Customer.retrieve(createdCustomer.getId())
                           .getDeleted());

    }

    @Test
    void shouldAlwaysGetNonNullMetadata() throws StripeException {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .setName("stripe-mock test")
                                                                       .build());
        assertEquals(Collections.emptyMap(), createdCustomer.getMetadata());
    }

    @Test
    void shouldSetDefaultSource() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .build());
        Customer updatedCustomer = customer.update(CustomerUpdateParams.builder()
                                                                       .setSource("tok_amex")
                                                                       .build());

        assertNull(updatedCustomer.getSources());
        assertNotNull(updatedCustomer.getDefaultSource()); // todo. we should turn this into a real card_xxx value

        Customer retrievedCustomer = Customer.retrieve(customer.getId());
        assertEquals(updatedCustomer, retrievedCustomer);
    }

    @Test
    void shouldNotBeAbleToUnsetDefaultSource() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .setSource("tok_amex")
                                                                .build());
        InvalidRequestException cannotUnsetDefaultSource = assertThrows(InvalidRequestException.class,
                                                                        () -> customer.update(CustomerUpdateParams.builder()
                                                                                                                  .setDefaultSource(EmptyParam.EMPTY)
                                                                                                                  .build()));
        assertEquals(
                "You passed an empty string for 'default_source'. We assume empty values are an attempt to unset a parameter; however 'default_source' cannot be unset. You should remove 'default_source' from your request or supply a non-empty value.",
                cannotUnsetDefaultSource.getStripeError()
                                        .getMessage());
        assertEquals("parameter_invalid_empty",
                     cannotUnsetDefaultSource.getStripeError()
                                             .getCode());
        assertEquals("invalid_request_error",
                     cannotUnsetDefaultSource.getStripeError()
                                             .getType());

    }

    @Test
    void shouldNotSetDefaultPaymentMethodAtCustomerCreation() {
        InvalidRequestException noSuchPaymentMethod = assertThrows(InvalidRequestException.class,
                                                                   () -> Customer.create(CustomerCreateParams.builder()
                                                                                                             .setName("stripe-mock test")
                                                                                                             .setInvoiceSettings(CustomerCreateParams.InvoiceSettings.builder()
                                                                                                                                                                     .setDefaultPaymentMethod(
                                                                                                                                                                             "tok_mastercard")
                                                                                                                                                                     .build())
                                                                                                             .build()));
        assertEquals(
                "No such PaymentMethod: 'tok_mastercard'; It's possible this PaymentMethod exists on one of your connected accounts, in which case you should retry this request on that connected account. Learn more at https://stripe.com/docs/connect/authentication",
                noSuchPaymentMethod.getStripeError()
                                   .getMessage());
        assertEquals("resource_missing",
                     noSuchPaymentMethod.getStripeError()
                                        .getCode());
        assertEquals("invalid_request_error",
                     noSuchPaymentMethod.getStripeError()
                                        .getType());
    }

    @Test
    void shouldUnsetDefaultPaymentMethod() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("stripe-mock test")
                                                                .build());
        PaymentMethod.create(PaymentMethodCreateParams.builder()
                                                      .setType(PaymentMethodCreateParams.Type.CARD)
                                                      .setCard(PaymentMethodCreateParams.Token.builder()
                                                                                              .setToken("tok_mastercard")
                                                                                              .build())
                                                      .build())
                     .attach(PaymentMethodAttachParams.builder()
                                                      .setCustomer(customer.getId())
                                                      .build());
        Customer updatedCustomer = customer.update(CustomerUpdateParams.builder()
                                                                       .setInvoiceSettings(CustomerUpdateParams.InvoiceSettings.builder()
                                                                                                                               .setDefaultPaymentMethod(
                                                                                                                                       EmptyParam.EMPTY)
                                                                                                                               .build())
                                                                       .build());
        assertNull(updatedCustomer.getInvoiceSettings()
                                  .getDefaultPaymentMethod());
        assertNull(Customer.retrieve(customer.getId())
                           .getInvoiceSettings()
                           .getDefaultPaymentMethod());
    }
}
