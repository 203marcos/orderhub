package com.orderhub.order.exception;

import java.util.UUID;

public class ProductUnavailableException extends RuntimeException {
    public ProductUnavailableException(UUID productId) {
        super("Product not available: " + productId);
    }
}
