package com.sesame.oss.stripemock.entities;

import com.sesame.oss.stripemock.http.ResponseCodeException;
import com.stripe.model.Product;

import java.time.Clock;
import java.util.Optional;

class ProductManager extends AbstractEntityManager<Product> {
    protected ProductManager(Clock clock) {
        super(clock, Product.class, "prod");
    }

    @Override
    public Optional<Product> delete(String id) throws ResponseCodeException {
        Product product = entities.remove(id);
        if (product == null) {
            return Optional.empty();
        }
        product.setDeleted(true);
        return Optional.of(product);
    }
}
