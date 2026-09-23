package com.orderhub.payment.event;

import com.orderhub.common.outbox.DomainEvent;

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
) implements DomainEvent {

    public static final String TOPIC = "payment.approved";

    @Override public String aggregateType() { return "Payment"; }

    // Keyed by order, not payment: order-service consumes this to settle that order, and the
    // key is what keeps a payment's events on a single partition in order.
    @Override public UUID aggregateId() { return orderId; }

    @Override public String eventType() { return "PaymentApproved"; }
    @Override public String topic() { return TOPIC; }
}
