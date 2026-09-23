package com.orderhub.notification.kafka;

import com.orderhub.notification.event.PaymentApprovedEvent;
import com.orderhub.notification.event.PaymentFailedEvent;
import com.orderhub.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final NotificationService notificationService;

    public PaymentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "payment.approved", groupId = "notification-service")
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Received PaymentApproved for order {}", event.orderId());
        notificationService.sendOrderConfirmation(event);
    }

    @KafkaListener(topics = "payment.failed", groupId = "notification-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailed for order {}", event.orderId());
        notificationService.sendPaymentFailedNotification(event);
    }
}
