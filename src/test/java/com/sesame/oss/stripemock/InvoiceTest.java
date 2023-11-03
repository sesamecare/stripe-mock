package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.InvoiceCreateParams;
import com.stripe.param.InvoiceUpdateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InvoiceTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        InvoiceCreateParams input = InvoiceCreateParams.builder()
                                                       .setCurrency("usd")
                                                       .setCustomer(customer.getId())
                                                       .setDescription("this is an invoice")
                                                       .putMetadata("integration_test", "true")
                                                       .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Invoice p1 = Invoice.create(input, options);
        Invoice p2 = Invoice.create(input, options);
        assertEquals(p1, p2);
        p1.delete();
    }

    // todo: test with no customer: com.stripe.exception.InvalidRequestException: Must provide customer or from_invoice.; code: parameter_missing; request-id: req_bNHG8oL17VjRHS

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());

        String idempotencyKey = String.valueOf(Math.random());
        Invoice invoice = Invoice.create(InvoiceCreateParams.builder()
                                                            .setCustomer(customer.getId())
                                                            .setDescription("description 1")
                                                            .putMetadata("integration_test", "true")
                                                            .build(),
                                         RequestOptions.builder()
                                                       .setIdempotencyKey(idempotencyKey)
                                                       .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Invoice.create(InvoiceCreateParams.builder()
                                                                                                         .setCustomer(customer.getId())
                                                                                                         .setDescription("description 2")
                                                                                                         .putMetadata("integration_test", "true")
                                                                                                         .build(),
                                                                                      RequestOptions.builder()
                                                                                                    .setIdempotencyKey(idempotencyKey)
                                                                                                    .build()));
        assertEquals(String.format(
                             "Keys for idempotent requests can only be used with the same parameters they were first used with. Try using a key other than '%s' if you meant to execute a different request.",
                             idempotencyKey),
                     idempotencyException.getStripeError()
                                         .getMessage());
        invoice.delete();
    }

    @Test
    void testInvoice() throws Exception {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        Invoice createdInvoice = //
                Invoice.create(InvoiceCreateParams.builder()
                                                  .setCustomer(customer.getId())
                                                  .setDescription("this is an invoice")
                                                  .putMetadata("integration_test", "true")
                                                  .build());

        Invoice retrievedInvoice = Invoice.retrieve(createdInvoice.getId());
        assertEquals(createdInvoice, retrievedInvoice);

        Invoice updatedInvoice = //
                retrievedInvoice.update(InvoiceUpdateParams.builder()
                                                           .setDescription("A new description")
                                                           .build(),
                                        RequestOptions.builder()
                                                      .build());

        Invoice retrievedUpdatedInvoice = Invoice.retrieve(createdInvoice.getId());
        assertEquals(updatedInvoice, retrievedUpdatedInvoice);

        Invoice deletedInvoice = createdInvoice.delete();
        assertTrue(deletedInvoice.getDeleted());

        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class, () -> Invoice.retrieve(createdInvoice.getId()));
        assertEquals(String.format("No such invoice: '%s'", createdInvoice.getId()),
                     invalidRequestException.getStripeError()
                                            .getMessage());
        assertEquals("resource_missing", invalidRequestException.getCode());
        assertEquals("invalid_request_error",
                     invalidRequestException.getStripeError()
                                            .getType());
    }

    @Test
    void shouldNotBeAbleToFetchUnknownInvoice() {
        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class, () -> Invoice.retrieve("in_nope"));
        assertEquals(String.format("No such invoice: '%s'", "in_nope"),
                     invalidRequestException.getStripeError()
                                            .getMessage());
        assertEquals("resource_missing", invalidRequestException.getCode());
        assertEquals("invalid_request_error",
                     invalidRequestException.getStripeError()
                                            .getType());
    }

    @Test
    void shouldFinalizeInvoice() throws StripeException {
        Customer customer = Customer.create(CustomerCreateParams.builder()
                                                                .setName("Mike Smith")
                                                                .build());
        Invoice createdInvoice = //
                Invoice.create(InvoiceCreateParams.builder()
                                                  .setCustomer(customer.getId())
                                                  .setDescription("this is an invoice")
                                                  .putMetadata("integration_test", "true")
                                                  .build());
        Invoice finalizedInvoice = createdInvoice.finalizeInvoice();
        assertEquals("open", finalizedInvoice.getStatus());

        Invoice retrievedfinalizedInvoice = Invoice.retrieve(createdInvoice.getId());
        assertEquals(finalizedInvoice, retrievedfinalizedInvoice);
    }
}
