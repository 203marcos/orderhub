package com.orderhub.order.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        UUID userId,
        String reason,
        LocalDateTime processedAt
) {}
