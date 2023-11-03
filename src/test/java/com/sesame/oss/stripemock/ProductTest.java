package com.sesame.oss.stripemock;

import com.stripe.exception.IdempotencyException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Product;
import com.stripe.net.RequestOptions;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.ProductUpdateParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProductTest extends AbstractStripeMockTest {
    @Test
    void shouldGetTheSameResponseForIdempotentRequests() throws StripeException {
        ProductCreateParams input = ProductCreateParams.builder()
                                                       .setName("Stripe-mock test product")
                                                       .putMetadata("integration_test", "true")
                                                       .build();
        RequestOptions options = RequestOptions.builder()
                                               .setIdempotencyKey(String.valueOf(Math.random()))
                                               .build();
        Product p1 = Product.create(input, options);
        Product p2 = Product.create(input, options);
        assertEquals(p1, p2);
        p1.delete();
    }

    @Test
    void shouldNotBeAbleToCreateDifferentEntitiesUsingTheSameIdempotencyKey() throws StripeException {
        String idempotencyKey = String.valueOf(Math.random());
        Product product = Product.create(ProductCreateParams.builder()
                                                            .setName("Stripe-mock test product")
                                                            .setDescription("description 1")
                                                            .putMetadata("integration_test", "true")
                                                            .build(),
                                         RequestOptions.builder()
                                                       .setIdempotencyKey(idempotencyKey)
                                                       .build());
        IdempotencyException idempotencyException = assertThrows(IdempotencyException.class,
                                                                 () -> Product.create(ProductCreateParams.builder()
                                                                                                         .setName("Stripe-mock test product")
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
        product.delete();
    }

    @Test
    void testProduct() throws Exception {
        Product createdProduct = //
                Product.create(ProductCreateParams.builder()
                                                  .setName("Stripe-mock test product")
                                                  .putMetadata("integration_test", "true")
                                                  .build());

        Product retrievedProduct = Product.retrieve(createdProduct.getId());
        assertEquals(createdProduct, retrievedProduct);

        Product updatedProduct = //
                retrievedProduct.update(ProductUpdateParams.builder()
                                                           .setDescription("A new description")
                                                           .build(),
                                        RequestOptions.builder()
                                                      .build());

        Product retrievedUpdatedProduct = Product.retrieve(createdProduct.getId());
        assertEquals(updatedProduct, retrievedUpdatedProduct);

        Product deletedProduct = createdProduct.delete();
        assertTrue(deletedProduct.getDeleted());

        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class, () -> Product.retrieve(createdProduct.getId()));
        assertEquals(String.format("No such product: '%s'", createdProduct.getId()),
                     invalidRequestException.getStripeError()
                                            .getMessage());
        assertEquals("resource_missing", invalidRequestException.getCode());
        assertEquals("invalid_request_error",
                     invalidRequestException.getStripeError()
                                            .getType());
    }

    @Test
    void shouldNotBeAbleToFetchUnknownProduct() {
        InvalidRequestException invalidRequestException = assertThrows(InvalidRequestException.class, () -> Product.retrieve("prod_nope"));
        assertEquals(String.format("No such product: '%s'", "prod_nope"),
                     invalidRequestException.getStripeError()
                                            .getMessage());
        assertEquals("resource_missing", invalidRequestException.getCode());
        assertEquals("invalid_request_error",
                     invalidRequestException.getStripeError()
                                            .getType());
    }
}
