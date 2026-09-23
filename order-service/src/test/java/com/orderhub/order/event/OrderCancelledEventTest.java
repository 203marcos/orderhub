package com.orderhub.order.event;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors {@link OrderCreatedEventTest}: {@code OrderTimeoutWatchdog} hands this record to
 * {@code OutboxRecorder} the same way {@code OrderService} hands over {@code OrderCreatedEvent},
 * so with {@code OutboxRecorder} mocked in {@code OrderTimeoutWatchdogTest}, the
 * {@link com.orderhub.common.outbox.DomainEvent} contract on this record is otherwise never
 * exercised anywhere in the suite. A typo in {@code eventType()} or {@code topic()} would reach
 * production silently — the row would still be written and published, just tagged wrong, and
 * whatever consumes {@code order.cancelled} (e.g. catalog-service releasing reserved stock)
 * would never fire.
 */
class OrderCancelledEventTest {

    private final UUID orderId = UUID.randomUUID();

    private OrderCancelledEvent anEvent() {
        return new OrderCancelledEvent(orderId, UUID.randomUUID(), "payment timed out", LocalDateTime.now());
    }

    @Test
    void shouldDeclareItsAggregateAsOrder() {
        assertThat(anEvent().aggregateType()).isEqualTo("Order");
    }

    @Test
    void shouldKeyTheOutboxRowByTheOrderIdNotTheUserId() {
        assertThat(anEvent().aggregateId()).isEqualTo(orderId);
    }

    @Test
    void shouldDeclareItsEventTypeAsOrderCancelled() {
        // A consumer reacting to this Saga step (e.g. catalog-service releasing stock) keys its
        // handling off this literal; changing it silently would break that without a compile error.
        assertThat(anEvent().eventType()).isEqualTo("OrderCancelled");
    }

    @Test
    void shouldRouteToTheOrderCancelledTopic() {
        assertThat(anEvent().topic()).isEqualTo("order.cancelled").isEqualTo(OrderCancelledEvent.TOPIC);
    }

    @Test
    void shouldCarryTheReasonAndWhenItHappened() {
        LocalDateTime now = LocalDateTime.now();
        OrderCancelledEvent event = new OrderCancelledEvent(orderId, UUID.randomUUID(), "payment timed out", now);

        assertThat(event.reason()).isEqualTo("payment timed out");
        assertThat(event.occurredAt()).isEqualTo(now);
    }
}
