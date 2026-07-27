package com.orderhub.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

@FeignClient(
        name = "catalog-service",
        url = "${catalog.service.url:http://localhost:8082}",
        fallbackFactory = CatalogClientFallback.class)
public interface CatalogClient {

    @GetMapping("/api/v1/products/{id}")
    ProductResponse getProduct(@PathVariable UUID id);

    record ProductResponse(UUID id, String name, BigDecimal price, boolean available) {}
}
