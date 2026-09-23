package com.orderhub.payment.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code PaymentService} never calls these directly — {@code OutboxRecorder} does, once the
 * event reaches it — so with {@code OutboxRecorder} mocked in {@code PaymentServiceTest}, this
 * record's {@link com.orderhub.common.outbox.DomainEvent} contract is otherwise never exercised
 * anywhere in the suite. The keying choice in particular is easy to get backwards silently:
 * a swap to {@code paymentId} would still compile and still publish, it would just stop
 * guaranteeing per-order ordering.
 */
class PaymentApprovedEventTest {

    private final UUID orderId = UUID.randomUUID();

    private PaymentApprovedEvent anEvent() {
        return new PaymentApprovedEvent(
                orderId, UUID.randomUUID(), UUID.randomUUID(), "customer@example.com",
                new BigDecimal("31.80"), LocalDateTime.now());
    }

    @Test
    void shouldDeclareItsAggregateAsPayment() {
        assertThat(anEvent().aggregateType()).isEqualTo("Payment");
    }

    @Test
    void shouldKeyTheOutboxRowByTheOrderIdNotThePaymentId() {
        // order-service consumes this to settle the order; keying by paymentId instead would
        // scatter a payment's events for the same order across partitions.
        PaymentApprovedEvent event = anEvent();

        assertThat(event.aggregateId()).isEqualTo(orderId).isNotEqualTo(event.paymentId());
    }

    @Test
    void shouldDeclareItsEventTypeAsPaymentApproved() {
        assertThat(anEvent().eventType()).isEqualTo("PaymentApproved");
    }

    @Test
    void shouldRouteToThePaymentApprovedTopic() {
        assertThat(anEvent().topic()).isEqualTo("payment.approved").isEqualTo(PaymentApprovedEvent.TOPIC);
    }
}
