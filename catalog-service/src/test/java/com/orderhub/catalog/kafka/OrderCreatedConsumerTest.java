package com.orderhub.catalog.kafka;

import com.orderhub.catalog.event.OrderCreatedEvent;
import com.orderhub.catalog.service.StockReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCreatedConsumerTest {

    @Mock StockReservationService stockReservationService;

    @InjectMocks OrderCreatedConsumer consumer;

    private static OrderCreatedEvent event(UUID orderId) {
        return new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@example.com", List.of(),
                new BigDecimal("99.99"), LocalDateTime.now());
    }

    @Test
    void shouldHandOffTheEventToTheStockReservationService() {
        OrderCreatedEvent received = event(UUID.randomUUID());

        consumer.onOrderCreated(received);

        verify(stockReservationService).reserveStock(received);
    }

    @Test
    void shouldLetFailuresPropagateSoTheErrorHandlerCanRetryAndDeadLetter() {
        OrderCreatedEvent received = event(UUID.randomUUID());
        doThrow(new IllegalStateException("database down")).when(stockReservationService).reserveStock(received);

        // Swallowing here would acknowledge the offset and lose the reservation attempt
        // silently; the DefaultErrorHandler can only retry and dead-letter what it can see.
        assertThatThrownBy(() -> consumer.onOrderCreated(received))
                .isInstanceOf(IllegalStateException.class);
    }
}
