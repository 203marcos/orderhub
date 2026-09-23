package com.orderhub.payment.controller;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(paymentService.getById(id, userId));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponse> getPaymentByOrder(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(paymentService.getByOrderId(orderId, userId));
    }
}
