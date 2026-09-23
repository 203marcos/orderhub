package com.orderhub.order.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    public OrderItem() {}

    public OrderItem(Order order, UUID productId, String productName, BigDecimal price, Integer quantity) {
        this.order = order;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.quantity = quantity;
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public UUID getProductId() { return productId; }
    public String getProductName() { return productName; }
    public BigDecimal getPrice() { return price; }
    public Integer getQuantity() { return quantity; }
}
