package com.orderhub.order.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID userId,
        String userEmail,
        List<OrderItemEvent> items,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
    public record OrderItemEvent(
            UUID productId,
            String productName,
            BigDecimal price,
            Integer quantity
    ) {}
}
