package com.orderhub.order.event;

import com.orderhub.common.outbox.DomainEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published when an order leaves PENDING without ever being settled by the saga — today, only
 * because {@link com.orderhub.order.service.OrderTimeoutWatchdog} gave up waiting on
 * payment-service. Consumers (e.g. catalog-service releasing any reserved stock) key off
 * {@code eventType()} exactly like {@link OrderCreatedEvent}'s consumers do.
 */
public record OrderCancelledEvent(
        UUID orderId,
        UUID userId,
        String reason,
        LocalDateTime occurredAt
) implements DomainEvent {

    public static final String TOPIC = "order.cancelled";

    @Override public String aggregateType() { return "Order"; }
    @Override public UUID aggregateId() { return orderId; }
    @Override public String eventType() { return "OrderCancelled"; }
    @Override public String topic() { return TOPIC; }
}
