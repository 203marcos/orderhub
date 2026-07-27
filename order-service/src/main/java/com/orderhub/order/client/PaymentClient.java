package com.orderhub.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Synchronous read of a payment's detail from payment-service.
 *
 * <p>Commands flow asynchronously through Kafka (the Saga). This client is the
 * complementary <em>query</em> path: fetching the payment record for an order on
 * demand. The Pact consumer contract for this interaction lives in the tests.
 */
@FeignClient(name = "payment-service", url = "${payment.service.url:http://localhost:8084}")
public interface PaymentClient {

    @GetMapping("/api/v1/payments/order/{orderId}")
    PaymentInfo getPaymentByOrder(@PathVariable UUID orderId);

    // Subset of payment-service's PaymentResponse that order-service actually needs.
    record PaymentInfo(UUID id, UUID orderId, UUID userId, BigDecimal amount, String status) {}
}
