package com.orderhub.payment.controller;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.exception.PaymentNotFoundException;
import com.orderhub.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP edge of payment-service: status codes and the JSON contract. No database — the
 * service itself is mocked.
 */
@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PaymentService paymentService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    private PaymentResponse aPayment() {
        return new PaymentResponse(paymentId, orderId, userId, new BigDecimal("31.80"),
                PaymentStatus.APPROVED, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{id}")
    class GetPayment {

        @Test
        @DisplayName("passes the caller's id to the service so ownership can be checked")
        void shouldPassTheCallersIdentity() throws Exception {
            when(paymentService.getById(paymentId, userId)).thenReturn(aPayment());

            mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(paymentId.toString()))
                    .andExpect(jsonPath("$.status").value("APPROVED"));

            // The controller must never look a payment up by id alone.
            verify(paymentService).getById(paymentId, userId);
        }

        @Test
        @DisplayName("returns 404 when the payment does not exist or is not the caller's")
        void shouldReturn404() throws Exception {
            when(paymentService.getById(paymentId, userId)).thenThrow(new PaymentNotFoundException(paymentId));

            mockMvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-User-Id", userId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 for a malformed payment id")
        void shouldRejectAMalformedId() throws Exception {
            mockMvc.perform(get("/api/v1/payments/{id}", "not-a-uuid").header("X-User-Id", userId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when the gateway's identity header is missing")
        void shouldRejectARequestWithoutIdentity() throws Exception {
            mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/order/{orderId}")
    class GetPaymentByOrder {

        @Test
        @DisplayName("returns the payment for the caller's order")
        void shouldReturnThePayment() throws Exception {
            when(paymentService.getByOrderId(orderId, userId)).thenReturn(aPayment());

            mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId).header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()));
        }

        @Test
        @DisplayName("returns 404 when the order has no payment owned by the caller")
        void shouldReturn404() throws Exception {
            when(paymentService.getByOrderId(orderId, userId)).thenThrow(new PaymentNotFoundException(orderId));

            mockMvc.perform(get("/api/v1/payments/order/{orderId}", orderId).header("X-User-Id", userId))
                    .andExpect(status().isNotFound());
        }
    }
}
