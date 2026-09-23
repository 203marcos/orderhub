package com.orderhub.catalog.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * catalog-service's own copy of order-service's {@code OrderCancelledEvent} on the new
 * {@code order.cancelled} topic. A second, independent trigger for releasing a stock
 * reservation — see {@code PaymentFailedEvent} for the other one. Both funnel into the same
 * idempotent release so only one of them ever actually puts stock back.
 */
public record OrderCancelledEvent(
        UUID orderId,
        UUID userId,
        String reason,
        LocalDateTime occurredAt
) {}
