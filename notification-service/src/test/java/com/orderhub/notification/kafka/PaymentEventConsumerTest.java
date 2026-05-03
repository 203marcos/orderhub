package com.orderhub.notification.kafka;

import com.orderhub.notification.event.PaymentApprovedEvent;
import com.orderhub.notification.event.PaymentFailedEvent;
import com.orderhub.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock NotificationService notificationService;

    @InjectMocks PaymentEventConsumer consumer;

    @Test
    void shouldDelegatePaymentApprovedToNotificationService() {
        PaymentApprovedEvent event = new PaymentApprovedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "customer@example.com", new BigDecimal("49.99"), LocalDateTime.now()
        );

        consumer.onPaymentApproved(event);

        verify(notificationService).sendOrderConfirmation(event);
    }

    @Test
    void shouldDelegatePaymentFailedToNotificationService() {
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Insufficient funds", LocalDateTime.now()
        );

        consumer.onPaymentFailed(event);

        verify(notificationService).sendPaymentFailedNotification(event);
    }
}