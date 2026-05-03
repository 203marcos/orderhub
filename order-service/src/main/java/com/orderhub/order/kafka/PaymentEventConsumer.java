package com.orderhub.order.kafka;

import com.orderhub.order.event.PaymentApprovedEvent;
import com.orderhub.order.event.PaymentFailedEvent;
import com.orderhub.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final OrderService orderService;

    public PaymentEventConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(topics = "payment.approved", groupId = "order-service")
    public void onPaymentApproved(PaymentApprovedEvent event) {
        log.info("Received PaymentApproved for order {}", event.orderId());
        orderService.confirmOrder(event.orderId());
    }

    @KafkaListener(topics = "payment.failed", groupId = "order-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailed for order {}: {}", event.orderId(), event.reason());
        orderService.failOrder(event.orderId());
    }
}
