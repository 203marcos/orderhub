package com.orderhub.catalog.kafka;

import com.orderhub.catalog.event.OrderCancelledEvent;
import com.orderhub.catalog.event.PaymentFailedEvent;
import com.orderhub.catalog.service.StockReservationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockReleaseConsumerTest {

    @Mock StockReservationService stockReservationService;

    @InjectMocks StockReleaseConsumer consumer;

    @Test
    void shouldReleaseOnPaymentFailedWithThatTriggerLabel() {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Insufficient funds", LocalDateTime.now());

        consumer.onPaymentFailed(event);

        verify(stockReservationService).releaseReservation(event.orderId(), "payment.failed");
    }

    @Test
    void shouldReleaseOnOrderCancelledWithThatTriggerLabel() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Customer changed their mind", LocalDateTime.now());

        consumer.onOrderCancelled(event);

        verify(stockReservationService).releaseReservation(event.orderId(), "order.cancelled");
    }
}
