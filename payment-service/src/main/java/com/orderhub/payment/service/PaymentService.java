package com.orderhub.payment.service;

import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.event.PaymentApprovedEvent;
import com.orderhub.payment.event.PaymentFailedEvent;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.gateway.PaymentDecision;
import com.orderhub.payment.gateway.PaymentGateway;
import com.orderhub.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRecorder outboxRecorder;
    private final PaymentGateway paymentGateway;

    public PaymentService(PaymentRepository paymentRepository, OutboxRecorder outboxRecorder,
                          PaymentGateway paymentGateway) {
        this.paymentRepository = paymentRepository;
        this.outboxRecorder = outboxRecorder;
        this.paymentGateway = paymentGateway;
    }

    /**
     * Kafka delivers at least once, so this can be called more than once for the same order.
     * {@code payments.order_id} is unique, so a blind insert would throw and the record would
     * be retried forever; an already-processed order is simply acknowledged instead.
     *
     * <p>The outcome event is staged in the outbox rather than sent to Kafka: the payment row
     * and its event commit together, so order-service can never be told about a payment that
     * was rolled back — nor left waiting on one that was recorded. See OutboxPublisher.
     */
    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Payment already exists for order {}, ignoring duplicate event", event.orderId());
            return;
        }

        Payment payment = new Payment(
                event.orderId(), event.userId(), event.userEmail(), event.totalAmount());

        PaymentDecision decision = paymentGateway.authorize(event.orderId(), event.totalAmount());

        if (decision.approved()) {
            recordApproval(payment, event);
        } else {
            recordDecline(payment, event, decision.declineReason());
        }
    }

    private void recordApproval(Payment payment, OrderCreatedEvent event) {
        payment.approve();
        Payment saved = paymentRepository.save(payment);
        outboxRecorder.record(new PaymentApprovedEvent(
                event.orderId(), saved.getId(), event.userId(), event.userEmail(),
                event.totalAmount(), LocalDateTime.now()));
    }

    private void recordDecline(Payment payment, OrderCreatedEvent event, String reason) {
        payment.fail(reason);
        paymentRepository.save(payment);
        outboxRecorder.record(new PaymentFailedEvent(
                event.orderId(), event.userId(), reason, LocalDateTime.now()));
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
}
