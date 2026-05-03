package com.orderhub.payment.service;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.event.PaymentApprovedEvent;
import com.orderhub.payment.event.PaymentFailedEvent;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.kafka.PaymentEventProducer;
import com.orderhub.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventProducer eventProducer;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventProducer eventProducer) {
        this.paymentRepository = paymentRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void processPayment(OrderCreatedEvent event) {
        Payment payment = new Payment(
                event.orderId(),
                event.userId(),
                event.userEmail(),
                event.totalAmount()
        );

        boolean approved = simulatePaymentGateway(event);

        if (approved) {
            payment.approve();
            paymentRepository.save(payment);
            eventProducer.publishApproved(new PaymentApprovedEvent(
                    event.orderId(),
                    payment.getId(),
                    event.userId(),
                    event.userEmail(),
                    event.totalAmount(),
                    LocalDateTime.now()
            ));
        } else {
            payment.fail("Insufficient funds");
            paymentRepository.save(payment);
            eventProducer.publishFailed(new PaymentFailedEvent(
                    event.orderId(),
                    event.userId(),
                    "Insufficient funds",
                    LocalDateTime.now()
            ));
        }
    }

    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(orderId));
    }

    public PaymentResponse getById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    private boolean simulatePaymentGateway(OrderCreatedEvent event) {
        // Simulate: orders over 10000 fail; all others succeed
        return event.totalAmount().compareTo(new java.math.BigDecimal("10000")) < 0;
    }
}
