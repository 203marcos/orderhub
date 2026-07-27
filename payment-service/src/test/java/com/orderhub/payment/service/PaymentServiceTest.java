package com.orderhub.payment.service;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.kafka.PaymentEventProducer;
import com.orderhub.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventProducer eventProducer;

    @InjectMocks PaymentService paymentService;

    private UUID orderId;
    private UUID userId;
    private String userEmail;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        userId = UUID.randomUUID();
        userEmail = "user@example.com";
    }

    @Test
    void shouldApprovePaymentForNormalOrder() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, userId, userEmail, List.of(), new BigDecimal("99.99"), LocalDateTime.now()
        );
        Payment savedPayment = new Payment(orderId, userId, userEmail, new BigDecimal("99.99"));
        savedPayment.approve();
        when(paymentRepository.save(any())).thenReturn(savedPayment);

        paymentService.processPayment(event);

        verify(paymentRepository).save(any());
        verify(eventProducer).publishApproved(any());
        verify(eventProducer, never()).publishFailed(any());
    }

    @Test
    void shouldFailPaymentForHighValueOrder() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, userId, userEmail, List.of(), new BigDecimal("15000.00"), LocalDateTime.now()
        );
        Payment savedPayment = new Payment(orderId, userId, userEmail, new BigDecimal("15000.00"));
        savedPayment.fail("Insufficient funds");
        when(paymentRepository.save(any())).thenReturn(savedPayment);

        paymentService.processPayment(event);

        verify(eventProducer).publishFailed(any());
        verify(eventProducer, never()).publishApproved(any());
    }

    @Test
    void shouldIgnoreDuplicateOrderCreatedEvent() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, userId, userEmail, List.of(), new BigDecimal("99.99"), LocalDateTime.now()
        );
        Payment existing = new Payment(orderId, userId, userEmail, new BigDecimal("99.99"));
        existing.approve();
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        // Kafka is at-least-once and payments.order_id is unique: a blind insert would
        // throw and the record would be retried forever.
        paymentService.processPayment(event);

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(eventProducer);
    }

    @Test
    void shouldGetPaymentByOrderId() {
        Payment payment = new Payment(orderId, userId, userEmail, new BigDecimal("50.00"));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getByOrderId(orderId, userId);

        assertThat(response.orderId()).isEqualTo(orderId);
    }

    @Test
    void shouldGetPaymentById() {
        UUID paymentId = UUID.randomUUID();
        Payment payment = new Payment(orderId, userId, userEmail, new BigDecimal("50.00"));
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getById(paymentId, userId);

        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void shouldThrowWhenPaymentNotFoundByOrderId() {
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getByOrderId(orderId, userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void shouldThrowWhenPaymentNotFoundById() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getById(paymentId, userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void shouldHidePaymentOwnedBySomeoneElse() {
        Payment payment = new Payment(orderId, UUID.randomUUID(), "other@example.com", new BigDecimal("50.00"));
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

        // Reported as "not found", not "forbidden", so ids cannot be enumerated.
        assertThatThrownBy(() -> paymentService.getByOrderId(orderId, userId))
                .isInstanceOf(PaymentNotFoundException.class);
    }
}
