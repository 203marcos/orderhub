package com.orderhub.payment.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * See {@link PaymentApprovedEventTest} — same reasoning applies here: with {@code OutboxRecorder}
 * mocked in {@code PaymentServiceTest}, this record's
 * {@link com.orderhub.common.outbox.DomainEvent} contract is otherwise never exercised.
 */
class PaymentFailedEventTest {

    private final UUID orderId = UUID.randomUUID();

    private PaymentFailedEvent anEvent() {
        return new PaymentFailedEvent(orderId, UUID.randomUUID(), "insufficient funds", LocalDateTime.now());
    }

    @Test
    void shouldDeclareItsAggregateAsPayment() {
        assertThat(anEvent().aggregateType()).isEqualTo("Payment");
    }

    @Test
    void shouldKeyTheOutboxRowByTheOrderId() {
        // order-service consumes this to settle the order that failed payment.
        assertThat(anEvent().aggregateId()).isEqualTo(orderId);
    }

    @Test
    void shouldDeclareItsEventTypeAsPaymentFailed() {
        assertThat(anEvent().eventType()).isEqualTo("PaymentFailed");
    }

    @Test
    void shouldRouteToThePaymentFailedTopic() {
        assertThat(anEvent().topic()).isEqualTo("payment.failed").isEqualTo(PaymentFailedEvent.TOPIC);
    }
}
