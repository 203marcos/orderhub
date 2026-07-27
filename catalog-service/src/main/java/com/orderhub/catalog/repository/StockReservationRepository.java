package com.orderhub.catalog.repository;

import com.orderhub.catalog.entity.StockReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    Optional<StockReservation> findByOrderId(UUID orderId);

    /**
     * Flips RESERVED -> RELEASED atomically. {@code payment.failed} and {@code order.cancelled}
     * both call this for the same order; the conditional {@code WHERE status = RESERVED} lets
     * only the first caller win — every other caller (a redelivery of either event, or the
     * other trigger arriving after) sees zero rows updated and must not touch stock again.
     *
     * @return 1 if this call performed the transition, 0 if the reservation was already
     *     RELEASED/FAILED or does not exist.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE StockReservation r SET r.status = com.orderhub.catalog.entity.StockReservationStatus.RELEASED "
            + "WHERE r.orderId = :orderId AND r.status = com.orderhub.catalog.entity.StockReservationStatus.RESERVED")
    int releaseIfReserved(@Param("orderId") UUID orderId);
}
