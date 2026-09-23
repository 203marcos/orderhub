package com.orderhub.notification.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentFailedEvent(
        UUID orderId,
        UUID userId,
        String reason,
        LocalDateTime processedAt
) {}
