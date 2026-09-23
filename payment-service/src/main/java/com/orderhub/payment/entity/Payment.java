package com.orderhub.payment.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID orderId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.PROCESSING;

    @Column
    private String failureReason;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime processedAt;

    public Payment() {}

    public Payment(UUID orderId, UUID userId, String userEmail, BigDecimal amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.userEmail = userEmail;
        this.amount = amount;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getProcessedAt() { return processedAt; }

    public void approve() { this.status = PaymentStatus.APPROVED; }
    public void fail(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }
}
