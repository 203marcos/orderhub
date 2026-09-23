package com.orderhub.payment.kafka;

import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.service.PaymentService;
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

    @Mock PaymentService paymentService;

    @InjectMocks OrderCreatedConsumer consumer;

    private static OrderCreatedEvent event(UUID orderId) {
        return new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@example.com", List.of(),
                new BigDecimal("99.99"), LocalDateTime.now());
    }

    @Test
    void shouldHandOffTheEventToThePaymentService() {
        OrderCreatedEvent received = event(UUID.randomUUID());

        consumer.onOrderCreated(received);

        verify(paymentService).processPayment(received);
    }

    @Test
    void shouldLetFailuresPropagateSoTheErrorHandlerCanRetryAndDeadLetter() {
        OrderCreatedEvent received = event(UUID.randomUUID());
        doThrow(new IllegalStateException("database down")).when(paymentService).processPayment(received);

        // Swallowing here would acknowledge the offset and lose the order silently; the
        // DefaultErrorHandler can only retry and dead-letter what it is allowed to see.
        assertThatThrownBy(() -> consumer.onOrderCreated(received))
                .isInstanceOf(IllegalStateException.class);
    }
}
