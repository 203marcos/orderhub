package com.orderhub.catalog.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The compensating-transaction record of the Saga: one row per order that catalog-service has
 * reserved stock for. {@code orderId} is unique so a redelivered {@code order.created} can
 * never reserve the same order twice (mirrors {@code payments.order_id} in payment-service).
 */
@Entity
@Table(name = "stock_reservations")
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<StockReservationItem> items = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public StockReservation() {}

    public StockReservation(UUID orderId, StockReservationStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public void addItem(StockReservationItem item) { items.add(item); }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public StockReservationStatus getStatus() { return status; }
    public List<StockReservationItem> getItems() { return items; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
