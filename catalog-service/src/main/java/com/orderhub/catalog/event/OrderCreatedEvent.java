package com.orderhub.catalog.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * catalog-service's own copy of the event order-service publishes on {@code order.created}.
 * Services do not share event classes (see ARCHITECTURE.md) — this mirrors payment-service's
 * {@code OrderCreatedEvent} field for field, since both are consumers of the same topic.
 */
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
