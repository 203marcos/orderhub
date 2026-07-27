package com.orderhub.order.event;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OrderService} never calls these directly — {@code OutboxRecorder} does, once the
 * event reaches it — so with {@code OutboxRecorder} mocked in {@code OrderServiceTest}, the
 * {@link com.orderhub.common.outbox.DomainEvent} contract on this record is otherwise never
 * exercised anywhere in the suite. A typo in {@code eventType()} or a swapped id here would
 * reach production silently: the row would still be written and published, just tagged wrong.
 */
class OrderCreatedEventTest {

    private final UUID orderId = UUID.randomUUID();

    private OrderCreatedEvent anEvent() {
        return new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@example.com",
                List.of(new OrderCreatedEvent.OrderItemEvent(
                        UUID.randomUUID(), "Burger", new BigDecimal("25.90"), 2)),
                new BigDecimal("51.80"), LocalDateTime.now());
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
    void shouldDeclareItsEventTypeAsOrderCreated() {
        // Consumers on the other side of the Saga (payment-service) key their idempotency
        // guards off this literal; changing it silently would break them without a compile error.
        assertThat(anEvent().eventType()).isEqualTo("OrderCreated");
    }

    @Test
    void shouldRouteToTheOrderCreatedTopic() {
        assertThat(anEvent().topic()).isEqualTo("order.created").isEqualTo(OrderCreatedEvent.TOPIC);
    }
}
