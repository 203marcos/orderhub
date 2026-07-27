package com.orderhub.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderItemRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull @Min(value = 1, message = "Quantity must be at least 1")
        Integer quantity
) {}
