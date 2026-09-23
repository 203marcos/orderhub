package com.orderhub.order.exception;

import java.util.UUID;

/** Thrown when catalog-service cannot be reached to price an order item. */
public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException(UUID productId, Throwable cause) {
        super("Catalog service is unavailable while pricing product " + productId, cause);
    }
}
