package com.orderhub.catalog.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * catalog-service's own copy of payment-service's {@code PaymentFailedEvent} (field name
 * {@code processedAt} included, to match the wire format payment-service actually produces).
 * Consuming this releases the stock reserved for the order — the saga's compensating path.
 */
public record PaymentFailedEvent(
        UUID orderId,
        UUID userId,
        String reason,
        LocalDateTime processedAt
) {}
