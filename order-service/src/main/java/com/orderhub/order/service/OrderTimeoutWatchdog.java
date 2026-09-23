package com.orderhub.order.service;

import com.orderhub.common.outbox.OutboxRecorder;
import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.OrderCancelledEvent;
import com.orderhub.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cancels orders that have been PENDING for too long — the answer to "what if payment-service
 * is down for hours?" Choreography has no timer of its own: if payment-service never publishes
 * {@code payment.approved} or {@code payment.failed} for an order, nothing else in the saga ever
 * moves it off PENDING. This is that timer.
 *
 * <p>Shaped like {@code OutboxPublisher} on purpose: a small {@code @Scheduled} component with
 * its own bounded batch and its own transaction, taking its configuration through the
 * constructor rather than a {@code @ConfigurationProperties} class, so a unit test can construct
 * it directly with fixed values.
 *
 * <pre>
 * every {@code orderhub.order.watchdog.fixed-delay-ms}, in one transaction:
 *   find up to {@code orderhub.order.watchdog.batch-size} orders PENDING since before the cutoff
 *   for each: try to move it to CANCELLED, but only if it is *still* PENDING
 *             (see {@link OrderRepository#transitionIfCurrentlyStatus})
 *   for each that actually moved: stage OrderCancelled in the outbox, same transaction
 * </pre>
 *
 * <p>The race this has to get right: payment-service comes back and approves (or fails) an
 * order right as it is about to time out. {@link OrderRepository#transitionIfCurrentlyStatus}'s
 * own {@code WHERE} clause — not this class's in-memory view of the {@link Order} the batch
 * query returned — is what decides the winner, so a concurrent saga outcome always makes the
 * timeout a silent no-op instead of overwriting whatever the saga already decided.
 */
@Component
public class OrderTimeoutWatchdog {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutWatchdog.class);
    private static final String TIMEOUT_REASON = "payment timed out";

    private final OrderRepository orderRepository;
    private final OutboxRecorder outboxRecorder;
    private final Duration pendingTimeout;
    private final int batchSize;

    public OrderTimeoutWatchdog(OrderRepository orderRepository,
                                OutboxRecorder outboxRecorder,
                                @Value("${orderhub.order.pending-timeout:30m}") Duration pendingTimeout,
                                @Value("${orderhub.order.watchdog.batch-size:100}") int batchSize) {
        this.orderRepository = orderRepository;
        this.outboxRecorder = outboxRecorder;
        this.pendingTimeout = pendingTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${orderhub.order.watchdog.fixed-delay-ms:60000}")
    @Transactional
    public void cancelTimedOutOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(pendingTimeout);
        List<Order> stuck = orderRepository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
                OrderStatus.PENDING, cutoff, Limit.of(batchSize));

        for (Order order : stuck) {
            int moved = orderRepository.transitionIfCurrentlyStatus(
                    order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);

            if (moved == 0) {
                // Lost the race: a saga outcome (or a previous tick) already settled this order
                // between the query above and this update. No event to stage — staging one
                // would tell the rest of the saga something happened that, as far as the
                // database is concerned, never did.
                log.debug("Order {} settled before the timeout could apply, skipping", order.getId());
                continue;
            }

            log.info("Order {} has been PENDING since {}, past the {} timeout — cancelling",
                    order.getId(), order.getCreatedAt(), pendingTimeout);
            outboxRecorder.record(new OrderCancelledEvent(
                    order.getId(), order.getUserId(), TIMEOUT_REASON, LocalDateTime.now()));
        }
    }
}
