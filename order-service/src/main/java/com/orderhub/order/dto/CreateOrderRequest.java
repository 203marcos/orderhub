package com.orderhub.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "Order must have at least one item")
        @Size(max = 50, message = "Order must have at most 50 items")
        @Valid
        List<OrderItemRequest> items
) {}
