package com.orderhub.catalog.dto;

import com.orderhub.catalog.entity.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        String category,
        boolean available,
        int stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.isAvailable(),
                product.getStock(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
