package com.orderhub.order.client;

import com.orderhub.order.exception.CatalogUnavailableException;
import com.orderhub.order.exception.ProductUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientFallbackTest {

    private final UUID id = UUID.randomUUID();

    @Test
    void catalogFallbackMapsNotFoundToProductUnavailable() {
        CatalogClient fallback = new CatalogClientFallback().create(notFound());

        assertThatThrownBy(() -> fallback.getProduct(id))
                .isInstanceOf(ProductUnavailableException.class);
    }

    @Test
    void catalogFallbackMapsOtherFailuresToCatalogUnavailable() {
        CatalogClient fallback = new CatalogClientFallback().create(new RuntimeException("connection refused"));

        assertThatThrownBy(() -> fallback.getProduct(id))
                .isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void paymentFallbackDegradesToUnknownStatus() {
        PaymentClient fallback = new PaymentClientFallback().create(new RuntimeException("timeout"));

        UUID userId = UUID.randomUUID();
        PaymentClient.PaymentInfo info = fallback.getPaymentByOrder(id, userId);

        assertThat(info.orderId()).isEqualTo(id);
        assertThat(info.userId()).isEqualTo(userId);
        assertThat(info.status()).isEqualTo("UNKNOWN");
    }

    private FeignException.NotFound notFound() {
        Request request = Request.create(
                Request.HttpMethod.GET, "/api/v1/products/" + id, Collections.emptyMap(),
                null, StandardCharsets.UTF_8, new RequestTemplate());
        return new FeignException.NotFound("not found", request, null, Collections.emptyMap());
    }
}
