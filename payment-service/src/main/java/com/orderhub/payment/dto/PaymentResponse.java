package com.orderhub.payment.dto;

import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason,
        LocalDateTime createdAt,
        LocalDateTime processedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt(),
                payment.getProcessedAt()
        );
    }
}
