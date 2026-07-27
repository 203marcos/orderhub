package com.orderhub.order.kafka;

import com.orderhub.order.event.PaymentApprovedEvent;
import com.orderhub.order.event.PaymentFailedEvent;
import com.orderhub.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock OrderService orderService;

    @InjectMocks PaymentEventConsumer consumer;

    @Test
    void shouldConfirmTheOrderNamedByAnApprovalEvent() {
        UUID orderId = UUID.randomUUID();

        consumer.onPaymentApproved(new PaymentApprovedEvent(
                orderId, UUID.randomUUID(), UUID.randomUUID(), "customer@example.com",
                new BigDecimal("30.00"), LocalDateTime.now()));

        verify(orderService).confirmOrder(orderId);
        verifyNoMoreInteractions(orderService);
    }

    @Test
    void shouldFailTheOrderNamedByAFailureEvent() {
        UUID orderId = UUID.randomUUID();

        consumer.onPaymentFailed(new PaymentFailedEvent(
                orderId, UUID.randomUUID(), "Insufficient funds", LocalDateTime.now()));

        verify(orderService).failOrder(orderId);
        verifyNoMoreInteractions(orderService);
    }
}
