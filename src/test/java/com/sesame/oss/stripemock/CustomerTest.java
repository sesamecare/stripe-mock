package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.CustomerUpdateParams;
import com.stripe.param.CustomerUpdateParams.Address;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

public class CustomerTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        CustomerCreateParams input = CustomerCreateParams.builder()
                                                         .setName("Mike Smith")
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
                                            .setName("Mike Smith")
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
                                                                       .setName("Mike Smith")
                                                                       .build());
        assertEquals("",
                     createdCustomer.getMetadata()
                                    .get("entity_type"));
    }

    /**
     * todo: We should have this test for each entity, just like for the idempotency
     */
    @Test
    void shouldSupportOverridingTheId() throws StripeException {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .putMetadata(StripeMock.OVERRIDE_ID_FOR_TESTING, "cus_abc123")
                                                                       .putMetadata("integration_test", "true")
                                                                       .setName("Mike Smith")
                                                                       .build());
        assertEquals("cus_abc123", createdCustomer.getId());
    }

    @Test
    void testCustomer() throws Exception {
        Customer createdCustomer = Customer.create(CustomerCreateParams.builder()
                                                                       .setPhone("0000")
                                                                       .putMetadata("integration_test", "true")
                                                                       .setName("Mike Smith")
                                                                       .setBalance(100_000_00L)
                                                                       .setValidate(false)
                                                                       .setAddress(CustomerCreateParams.Address.builder()
                                                                                                               .setCity("Stockholm")
                                                                                                               .setCountry("Sweden")
                                                                                                               .build())
                                                                       .build());
        assertEquals("0000", createdCustomer.getPhone());
        assertEquals("Mike Smith", createdCustomer.getName());
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
                                                                       .setName("Mike Smith")
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
                                                                       .setName("Mike Smith")
                                                                       .build());
        assertEquals(Collections.emptyMap(), createdCustomer.getMetadata());
    }

    @Test
    void shouldSetDefaultSource() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        Customer updatedCustomer = customer.update(CustomerUpdateParams.builder()
                                                                       .setSource("tok_amex")
                                                                       .build());

        assertNull(updatedCustomer.getSources());
        assertNotNull(updatedCustomer.getDefaultSource()); // todo. we should turn this into a real card_xxx value

        Customer retrievedCustomer = Customer.retrieve(customer.getId());
        assertEquals(updatedCustomer, retrievedCustomer);
    }
}
