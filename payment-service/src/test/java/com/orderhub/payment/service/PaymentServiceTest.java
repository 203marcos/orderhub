package com.orderhub.payment.service;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.common.outbox.DomainEvent;
import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.event.PaymentFailedEvent;
import com.orderhub.payment.gateway.PaymentDecision;
import com.orderhub.payment.gateway.PaymentGateway;
import com.orderhub.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock OutboxRecorder outboxRecorder;
    @Mock PaymentGateway paymentGateway;

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
    void shouldStagePaymentApprovedWhenTheGatewayAuthorises() {
        OrderCreatedEvent event = orderFor(new BigDecimal("99.99"));
        Payment savedPayment = new Payment(orderId, userId, userEmail, new BigDecimal("99.99"));
        savedPayment.approve();
        when(paymentRepository.save(any())).thenReturn(savedPayment);
        when(paymentGateway.authorize(orderId, new BigDecimal("99.99")))
                .thenReturn(PaymentDecision.approve());

        paymentService.processPayment(event);

        verify(paymentRepository).save(any());
        assertThat(stagedEvent().topic()).isEqualTo("payment.approved");
    }

    @Test
    void shouldStagePaymentFailedWithTheGatewaysReasonWhenItDeclines() {
        OrderCreatedEvent event = orderFor(new BigDecimal("15000.00"));
        when(paymentRepository.save(any()))
                .thenReturn(new Payment(orderId, userId, userEmail, new BigDecimal("15000.00")));
        when(paymentGateway.authorize(orderId, new BigDecimal("15000.00")))
                .thenReturn(PaymentDecision.decline("Card limit exceeded"));

        paymentService.processPayment(event);

        DomainEvent staged = stagedEvent();
        assertThat(staged.topic()).isEqualTo("payment.failed");
        // The reason is the gateway's, not one this service invents.
        assertThat(((PaymentFailedEvent) staged).reason()).isEqualTo("Card limit exceeded");
    }

    private OrderCreatedEvent orderFor(BigDecimal total) {
        return new OrderCreatedEvent(orderId, userId, userEmail, List.of(), total, LocalDateTime.now());
    }

    /** The outcome is staged in the outbox inside the same transaction, not sent to Kafka here. */
    private DomainEvent stagedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRecorder).record(captor.capture());
        return captor.getValue();
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
        verifyNoInteractions(outboxRecorder, paymentGateway);
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
