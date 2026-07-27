package com.orderhub.order.client;

import com.orderhub.order.exception.CatalogUnavailableException;
import com.orderhub.order.exception.ProductUnavailableException;
import feign.FeignException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Runs when the circuit breaker around {@link CatalogClient} opens or the call fails.
 * A 404 is a business answer (product does not exist) → 422; anything else means the
 * catalog is unreachable and the order cannot be priced → fail fast with 503.
 */
@Component
public class CatalogClientFallback implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        return productId -> {
            if (cause instanceof FeignException.NotFound) {
                throw new ProductUnavailableException(productId);
            }
            throw new CatalogUnavailableException(productId, cause);
        };
    }
}
