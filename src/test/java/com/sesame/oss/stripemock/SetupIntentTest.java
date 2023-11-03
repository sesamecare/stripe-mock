package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.SetupIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.param.SetupIntentUpdateParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SetupIntentTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        SetupIntentCreateParams input = SetupIntentCreateParams.builder()
                                                               .setCustomer(customer.getId())
                                                               .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        SetupIntent pi1 = SetupIntent.create(input, options);
        SetupIntent pi2 = SetupIntent.create(input, options);
        assertEquals(pi1, pi2);
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        String idempotencyKey = String.valueOf(Math.random());
        SetupIntent.create(SetupIntentCreateParams.builder()
                                                  .setCustomer(customer.getId())
                                                  .setPaymentMethod("pm_card_visa")
                                                  .build(),
                           RequestOptions.builder()
                                         .setIdempotencyKey(idempotencyKey)
                                         .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> SetupIntent.create(SetupIntentCreateParams.builder()
                                                                                                                 .setCustomer(customer.getId())
                                                                                                                 .setPaymentMethod("pm_card_mastercard")
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

    //@Test
    void shouldNotCreateSetupIntentForNonexistentUser() {
        // todo: implement me!
        Assertions.fail("Implement me!");

        // todo: both for create and update
    }

    // todo: add test: com.stripe.exception.InvalidRequestException: The parameter `mandate_data` cannot be passed when creating a SetupIntent unless `confirm` is set to true.; code: setup_intent_invalid_parameter;

    @Test
    void testSetupIntent() throws Exception {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        SetupIntent createdSetupIntent = //
                SetupIntent.create(SetupIntentCreateParams.builder()
                                                          .putMetadata("integration_test", "true")
                                                          .setCustomer(customer.getId())
                                                          .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                                                          .build());
        assertNotNull(createdSetupIntent.getClientSecret());

        SetupIntent retrievedSetupIntent = SetupIntent.retrieve(createdSetupIntent.getId());
        assertEquals(createdSetupIntent, retrievedSetupIntent);

        // todo: test: com.stripe.exception.InvalidRequestException: The PaymentMethod cannot be attached to both the customer and to self. In order to attach a PaymentMethod to a customer, set `attach_to_self` to false.
        SetupIntent updatedSetupIntent = //
                retrievedSetupIntent.update(SetupIntentUpdateParams.builder()
                                                                   .setPaymentMethod("pm_card_mastercard")
                                                                   .build());

        SetupIntent retrievedUpdatedSetupIntent = SetupIntent.retrieve(createdSetupIntent.getId());
        assertEquals(updatedSetupIntent, retrievedUpdatedSetupIntent);
        // todo: also test things like SetupIntent.confirm(), .cancel(), verifyMicrodeposits().
    }
}
