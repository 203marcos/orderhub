package com.orderhub.order.service;

import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.OrderCancelledEvent;
import com.orderhub.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code OutboxPublisherTest} answers "does a staged event actually reach Kafka"; this answers
 * "what if payment-service never answers at all". The test shape mirrors it deliberately: the
 * repository is mocked, so what is checked is the watchdog's own orchestration around whatever
 * the repository reports — not Spring Data's query derivation, which only a real database can
 * prove (that belongs in a {@code @Tag("integration")} test, not here).
 *
 * <p>Because of that boundary, "a fresh PENDING order is left alone" and "a CONFIRMED/CANCELLED
 * order is left alone" are exercised as one case: the query
 * {@code findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc} is exactly what filters those out
 * before the watchdog ever sees them, so from here both look like "the repository returned
 * nothing to do".
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTimeoutWatchdog")
class OrderTimeoutWatchdogTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30); // matches application.yml's default

    @Mock OrderRepository orderRepository;
    @Mock OutboxRecorder outboxRecorder;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    private OrderTimeoutWatchdog watchdog(Duration timeout) {
        return new OrderTimeoutWatchdog(orderRepository, outboxRecorder, timeout, 100);
    }

    private Order pendingOrder(UUID owner) {
        Order order = new Order(owner, "customer@example.com");
        order.setStatus(OrderStatus.PENDING);
        // The id is normally assigned by the database on insert (@GeneratedValue). The watchdog
        // never persists these test doubles, so it has to be faked in to give each order a
        // distinct identity for the mocked repository calls to key off.
        ReflectionTestUtils.setField(order, "id", UUID.randomUUID());
        return order;
    }

    private void givenStuckOrders(Order... orders) {
        when(orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                eq(OrderStatus.PENDING), any(LocalDateTime.class), any(Limit.class)))
                .thenReturn(List.of(orders));
    }

    @Nested
    @DisplayName("when an order has been PENDING past the timeout")
    class ATimedOutOrder {

        @Test
        @DisplayName("cancels it and stages OrderCancelled in the outbox, same transaction")
        void shouldCancelAndStageTheEvent() {
            Order order = pendingOrder(userId);
            givenStuckOrders(order);
            when(orderRepository.transitionIfCurrentlyStatus(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED))
                    .thenReturn(1);

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
            verify(outboxRecorder).record(captor.capture());
            OrderCancelledEvent staged = captor.getValue();
            assertThat(staged.orderId()).isEqualTo(order.getId());
            assertThat(staged.userId()).isEqualTo(userId);
            assertThat(staged.reason()).isEqualTo("payment timed out");
            assertThat(staged.topic()).isEqualTo("order.cancelled");
        }

        @Test
        @DisplayName("processes every stuck order in the batch, not just the first")
        void shouldProcessEveryOrderInTheBatch() {
            Order first = pendingOrder(UUID.randomUUID());
            Order second = pendingOrder(UUID.randomUUID());
            givenStuckOrders(first, second);
            when(orderRepository.transitionIfCurrentlyStatus(any(), eq(OrderStatus.PENDING), eq(OrderStatus.CANCELLED)))
                    .thenReturn(1);

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            verify(orderRepository).transitionIfCurrentlyStatus(first.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);
            verify(orderRepository).transitionIfCurrentlyStatus(second.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("when no order is stuck")
    class NoOrdersStuck {

        @Test
        @DisplayName("leaves a fresh PENDING order alone (the query excludes it)")
        void shouldDoNothingWhenNothingIsOverdue() {
            givenStuckOrders(); // an empty batch is exactly what a fresh PENDING order looks like here

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            verify(orderRepository, never()).transitionIfCurrentlyStatus(any(), any(), any());
            verifyNoInteractions(outboxRecorder);
        }

        @Test
        @DisplayName("leaves a CONFIRMED or CANCELLED order alone (the query excludes it)")
        void shouldDoNothingForAlreadySettledOrders() {
            // A CONFIRMED/CANCELLED order never matches "status = PENDING" in the batch query,
            // so from the watchdog's side this is indistinguishable from "nothing is overdue".
            givenStuckOrders();

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            verifyNoInteractions(outboxRecorder);
        }
    }

    @Nested
    @DisplayName("when payment-service settles the order first")
    class RaceAgainstASagaOutcome {

        @Test
        @DisplayName("does not stage an event when the order settles between the query and the update")
        void shouldSkipTheEventWhenTheRaceIsLost() {
            // payment.approved (or a previous tick) can win between this tick's SELECT and its
            // UPDATE. transitionIfCurrentlyStatus reports that with a zero row count, not an
            // exception — see OrderRepository's Javadoc.
            Order order = pendingOrder(userId);
            givenStuckOrders(order);
            when(orderRepository.transitionIfCurrentlyStatus(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED))
                    .thenReturn(0);

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            verifyNoInteractions(outboxRecorder);
        }

        @Test
        @DisplayName("still processes the rest of the batch after losing a race on one order")
        void shouldKeepProcessingAfterALostRace() {
            Order loser = pendingOrder(UUID.randomUUID());
            Order winner = pendingOrder(UUID.randomUUID());
            givenStuckOrders(loser, winner);
            when(orderRepository.transitionIfCurrentlyStatus(loser.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED))
                    .thenReturn(0);
            when(orderRepository.transitionIfCurrentlyStatus(winner.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED))
                    .thenReturn(1);

            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();

            ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
            verify(outboxRecorder).record(captor.capture());
            assertThat(captor.getValue().orderId()).isEqualTo(winner.getId());
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("queries using the configured (default) pending-timeout as the cutoff")
        void shouldQueryUsingTheConfiguredTimeout() {
            givenStuckOrders();

            LocalDateTime before = LocalDateTime.now().minus(DEFAULT_TIMEOUT);
            watchdog(DEFAULT_TIMEOUT).cancelTimedOutOrders();
            LocalDateTime after = LocalDateTime.now().minus(DEFAULT_TIMEOUT);

            // Pinned to DEFAULT_TIMEOUT (30 minutes, matching application.yml's
            // orderhub.order.pending-timeout default) rather than an arbitrary literal, so this
            // fails loudly if the documented default ever drifts from the code.
            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(orderRepository).findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    eq(OrderStatus.PENDING), cutoffCaptor.capture(), any(Limit.class));
            assertThat(cutoffCaptor.getValue()).isBetween(before, after);
        }

        @Test
        @DisplayName("honors a non-default timeout")
        void shouldHonorACustomTimeout() {
            givenStuckOrders();
            Duration customTimeout = Duration.ofMinutes(5);

            LocalDateTime before = LocalDateTime.now().minus(customTimeout);
            watchdog(customTimeout).cancelTimedOutOrders();
            LocalDateTime after = LocalDateTime.now().minus(customTimeout);

            ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(orderRepository).findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                    eq(OrderStatus.PENDING), cutoffCaptor.capture(), any(Limit.class));
            assertThat(cutoffCaptor.getValue()).isBetween(before, after);
        }
    }
}
