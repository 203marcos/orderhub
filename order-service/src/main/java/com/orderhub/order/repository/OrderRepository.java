package com.orderhub.order.repository;

import com.orderhub.order.entity.Order;
import com.orderhub.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserId(UUID userId);

    /**
     * Candidates for the PENDING-order watchdog: orders still in {@code status} after
     * {@code cutoff}, oldest first.
     *
     * <p>Locked the same way {@code OutboxRepository} claims its batch — pessimistic write plus
     * {@code SKIP LOCKED} (the {@code -2} lock timeout) — so more than one order-service replica
     * can run the watchdog without both grabbing the same row. That is an efficiency guard, not
     * the correctness one: the correctness guard is {@link #transitionIfCurrentlyStatus}, whose
     * own {@code WHERE} clause is what actually decides who wins a race.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<Order> findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            OrderStatus status, LocalDateTime cutoff, Limit limit);

    /**
     * Moves an order to {@code to} only if it is currently {@code from} — atomically, at the
     * database, via this statement's own {@code WHERE} clause rather than an in-memory check.
     *
     * <p>That is what makes a race safe: if a concurrent saga outcome (a real payment.approved
     * or payment.failed) — or an earlier watchdog tick — already moved the order away from
     * {@code from}, this matches zero rows and the caller silently loses the race. It enforces
     * the same PENDING-only invariant {@code OrderService#applySagaOutcome} checks in memory for
     * the saga outcomes; here the race is expected rather than exceptional (a payment can be
     * approved in the same instant the watchdog decides to time the order out), so the guard has
     * to be atomic instead of read-then-write.
     *
     * @return the number of rows changed: 1 if this call won the race, 0 if it lost it.
     */
    @Modifying
    @Query("update Order o set o.status = :to where o.id = :id and o.status = :from")
    int transitionIfCurrentlyStatus(
            @Param("id") UUID id, @Param("from") OrderStatus from, @Param("to") OrderStatus to);
}
