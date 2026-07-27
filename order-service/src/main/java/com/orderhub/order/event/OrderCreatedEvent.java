package com.orderhub.order.event;

import com.orderhub.common.outbox.DomainEvent;

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
) implements DomainEvent {

    public static final String TOPIC = "order.created";

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
    @Override public String eventType() { return "OrderCreated"; }
    @Override public String topic() { return TOPIC; }

    public record OrderItemEvent(
            UUID productId,
            String productName,
            BigDecimal price,
            Integer quantity
    ) {}
}
