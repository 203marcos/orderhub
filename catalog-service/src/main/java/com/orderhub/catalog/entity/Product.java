package com.orderhub.catalog.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false)
    private BigDecimal price;

    private String category;

    @Column(nullable = false)
    private boolean available = true;

    // Reserved and released atomically by StockReservationService via a repository
    // @Modifying query — never read here, mutated in Java, and written back, which would
    // race under concurrent orders for the same product.
    @Column(nullable = false)
    private int stock = 0;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Product() {}

    public Product(String name, String description, BigDecimal price, String category, int stock) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.category = category;
        this.stock = stock;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getCategory() { return category; }
    public boolean isAvailable() { return available; }
    public int getStock() { return stock; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setCategory(String category) { this.category = category; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setStock(int stock) { this.stock = stock; }
}
