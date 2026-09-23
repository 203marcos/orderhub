package com.orderhub.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "stock_reservation_items")
public class StockReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_reservation_id", nullable = false)
    private StockReservation reservation;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private Integer quantity;

    public StockReservationItem() {}

    public StockReservationItem(StockReservation reservation, UUID productId, Integer quantity) {
        this.reservation = reservation;
        this.productId = productId;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public StockReservation getReservation() { return reservation; }
    public UUID getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}
