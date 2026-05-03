package com.orderhub.payment.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentApprovedEvent(
        UUID orderId,
        UUID paymentId,
        UUID userId,
        String userEmail,
        BigDecimal amount,
        LocalDateTime processedAt
) {}
