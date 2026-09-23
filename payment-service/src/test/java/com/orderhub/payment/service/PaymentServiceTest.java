package com.orderhub.payment.service;

import com.orderhub.common.outbox.DomainEvent;
import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.Payment;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.event.PaymentFailedEvent;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.gateway.PaymentDecision;
import com.orderhub.payment.gateway.PaymentGateway;
import com.orderhub.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
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

    // ---------------------------------------------------------------- helpers

    private OrderCreatedEvent orderFor(String total) {
        return new OrderCreatedEvent(
                orderId, userId, userEmail, List.of(), new BigDecimal(total), LocalDateTime.now());
    }

    private Payment paymentOwnedBy(UUID owner) {
        return new Payment(orderId, owner, "owner@example.com", new BigDecimal("50.00"));
    }

    private void givenTheGatewayAnswers(String amount, PaymentDecision decision) {
        when(paymentGateway.authorize(orderId, new BigDecimal(amount))).thenReturn(decision);
    }

    private void givenSaveReturnsTheArgument() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** The outcome is staged in the outbox inside the same transaction, not sent to Kafka here. */
    private DomainEvent stagedEvent() {
        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(outboxRecorder).record(captor.capture());
        return captor.getValue();
    }

    // ---------------------------------------------------------------- tests

    @Nested
    @DisplayName("when an OrderCreated event arrives")
    class ProcessingAPayment {

        @Test
        @DisplayName("records an approved payment and stages PaymentApproved")
        void shouldApproveWhenTheGatewayAuthorises() {
            givenSaveReturnsTheArgument();
            givenTheGatewayAnswers("99.99", PaymentDecision.approve());

            paymentService.processPayment(orderFor("99.99"));

            ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(stagedEvent().topic()).isEqualTo("payment.approved");
        }

        @Test
        @DisplayName("records a failed payment and stages PaymentFailed with the gateway's reason")
        void shouldDeclineWithTheGatewaysReason() {
            givenSaveReturnsTheArgument();
            givenTheGatewayAnswers("15000.00", PaymentDecision.decline("Card limit exceeded"));

            paymentService.processPayment(orderFor("15000.00"));

            DomainEvent staged = stagedEvent();
            assertThat(staged.topic()).isEqualTo("payment.failed");
            // The reason comes from the gateway; this service must not invent its own.
            assertThat(((PaymentFailedEvent) staged).reason()).isEqualTo("Card limit exceeded");
        }

        /**
         * Kafka is at-least-once, and {@code payments.order_id} is unique. A blind insert on
         * redelivery would violate the constraint, and the record would be retried until it
         * landed in the dead-letter topic — for an order that was, in fact, paid correctly.
         * The guard must hold whatever state the existing payment is in.
         */
        @ParameterizedTest(name = "an order already {0} is not charged again")
        @EnumSource(PaymentStatus.class)
        @DisplayName("ignores a redelivered event for an order already processed")
        void shouldIgnoreDuplicateEvents(PaymentStatus existingStatus) {
            Payment existing = paymentOwnedBy(userId);
            if (existingStatus == PaymentStatus.APPROVED) {
                existing.approve();
            } else if (existingStatus == PaymentStatus.FAILED) {
                existing.fail("Insufficient funds");
            }
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existing));

            paymentService.processPayment(orderFor("99.99"));

            verify(paymentRepository, never()).save(any());
            // Not even the gateway is called: charging twice is worse than failing loudly.
            verifyNoInteractions(outboxRecorder, paymentGateway);
        }
    }

    @Nested
    @DisplayName("when reading a payment")
    class ReadingAPayment {

        @Test
        @DisplayName("returns it to its owner, by order id")
        void shouldReturnByOrderIdToTheOwner() {
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(paymentOwnedBy(userId)));

            assertThat(paymentService.getByOrderId(orderId, userId).orderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("returns it to its owner, by payment id")
        void shouldReturnByIdToTheOwner() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(paymentOwnedBy(userId)));

            PaymentResponse response = paymentService.getById(paymentId, userId);

            assertThat(response.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("reports an unknown order as not found")
        void shouldReportUnknownOrderAsNotFound() {
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getByOrderId(orderId, userId))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("reports an unknown payment as not found")
        void shouldReportUnknownPaymentAsNotFound() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getById(paymentId, userId))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("reports someone else's payment as not found, not forbidden")
        void shouldHideAnotherUsersPayment() {
            when(paymentRepository.findByOrderId(orderId))
                    .thenReturn(Optional.of(paymentOwnedBy(UUID.randomUUID())));

            // 404 rather than 403: a 403 would confirm the id exists (OWASP API1).
            assertThatThrownBy(() -> paymentService.getByOrderId(orderId, userId))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
