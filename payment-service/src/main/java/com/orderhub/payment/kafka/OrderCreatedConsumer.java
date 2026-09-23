package com.orderhub.payment.kafka;

import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedConsumer.class);

    private final PaymentService paymentService;

    public OrderCreatedConsumer(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "order.created", groupId = "payment-service")
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreated event for order {}", event.orderId());
        paymentService.processPayment(event);
    }
}
