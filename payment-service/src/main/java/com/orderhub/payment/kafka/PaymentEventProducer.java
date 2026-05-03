package com.orderhub.payment.kafka;

import com.orderhub.payment.event.PaymentApprovedEvent;
import com.orderhub.payment.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventProducer.class);
    private static final String APPROVED_TOPIC = "payment.approved";
    private static final String FAILED_TOPIC = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishApproved(PaymentApprovedEvent event) {
        kafkaTemplate.send(APPROVED_TOPIC, event.orderId().toString(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish PaymentApproved for order {}", event.orderId(), ex);
                    else log.info("Published PaymentApproved for order {}", event.orderId());
                });
    }

    public void publishFailed(PaymentFailedEvent event) {
        kafkaTemplate.send(FAILED_TOPIC, event.orderId().toString(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish PaymentFailed for order {}", event.orderId(), ex);
                    else log.info("Published PaymentFailed for order {}", event.orderId());
                });
    }
}
