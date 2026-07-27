package com.orderhub.payment.service;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.event.PaymentApprovedEvent;
import com.orderhub.payment.event.PaymentFailedEvent;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.outbox.OutboxEvent;
import com.orderhub.payment.outbox.OutboxRepository;
import com.orderhub.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String APPROVED_TOPIC = "payment.approved";
    private static final String FAILED_TOPIC = "payment.failed";

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository, OutboxRepository outboxRepository,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Kafka delivers at least once, so this can be called more than once for the same order.
     * {@code payments.order_id} is unique, so a blind insert would throw and the record would
     * be retried forever; an already-processed order is simply acknowledged instead.
     */
    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Payment already exists for order {}, ignoring duplicate event", event.orderId());
            return;
        }

        Payment payment = new Payment(
                event.orderId(),
                event.userId(),
                event.userEmail(),
                event.totalAmount()
        );

        boolean approved = simulatePaymentGateway(event);

        // The outcome event goes to the outbox, not straight to Kafka: the payment row and its
        // event commit together, so order-service can never be told about a payment that was
        // rolled back — nor left waiting on one that was recorded. See OutboxPublisher.
        if (approved) {
            payment.approve();
            Payment saved = paymentRepository.save(payment);
            outboxRepository.save(new OutboxEvent(
                    "Payment", event.orderId(), "PaymentApproved", APPROVED_TOPIC,
                    serialize(new PaymentApprovedEvent(
                            event.orderId(),
                            saved.getId(),
                            event.userId(),
                            event.userEmail(),
                            event.totalAmount(),
                            LocalDateTime.now()))));
        } else {
            payment.fail("Insufficient funds");
            paymentRepository.save(payment);
            outboxRepository.save(new OutboxEvent(
                    "Payment", event.orderId(), "PaymentFailed", FAILED_TOPIC,
                    serialize(new PaymentFailedEvent(
                            event.orderId(),
                            event.userId(),
                            "Insufficient funds",
                            LocalDateTime.now()))));
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            // An event we cannot serialize is a programming error; failing here rolls the
            // payment back rather than committing one that order-service will never hear about.
            throw new IllegalStateException("Could not serialize payment event", ex);
        }
    }

    /**
     * Reads the payment for an order the caller owns. A payment belonging to someone else
     * is reported as "not found" rather than "forbidden", so the endpoint cannot be used to
     * probe which ids exist (OWASP API1 — Broken Object Level Authorization).
     */
    public PaymentResponse getByOrderId(UUID orderId, UUID userId) {
        return paymentRepository.findByOrderId(orderId)
                .filter(payment -> payment.getUserId().equals(userId))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    public PaymentResponse getById(UUID paymentId, UUID userId) {
        return paymentRepository.findById(paymentId)
                .filter(payment -> payment.getUserId().equals(userId))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private boolean simulatePaymentGateway(OrderCreatedEvent event) {
        // Simulate: orders over 10000 fail; all others succeed
        return event.totalAmount().compareTo(new java.math.BigDecimal("10000")) < 0;
    }
}
