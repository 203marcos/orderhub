package com.orderhub.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderhub.order.client.PaymentClient;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.exception.OrderNotFoundException;
import com.orderhub.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP edge of order-service: status codes, request validation and the JSON contract.
 *
 * <p>A slice test rather than a full context — no database, no Kafka. What is being checked
 * here is the wiring between HTTP and the service, which the integration test also exercises
 * but far more slowly and without covering the malformed-request cases.
 */
@WebMvcTest(OrderController.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean OrderService orderService;

    private final UUID userId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();

    private OrderResponse anOrder() {
        return new OrderResponse(
                orderId, userId, "customer@example.com", OrderStatus.PENDING,
                List.of(), new BigDecimal("31.80"), LocalDateTime.now());
    }

    private String bodyWithOneItem(int quantity) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("items", List.of(
                        Map.of("productId", UUID.randomUUID().toString(), "quantity", quantity))));
    }

    private String bodyWithItemCount(int itemCount) throws Exception {
        List<Map<String, Object>> items = IntStream.range(0, itemCount)
                .mapToObj(i -> Map.<String, Object>of(
                        "productId", UUID.randomUUID().toString(), "quantity", 1))
                .toList();
        return objectMapper.writeValueAsString(Map.of("items", items));
    }

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrder {

        @Test
        @DisplayName("returns 201 with the created order")
        void shouldReturn201() throws Exception {
            when(orderService.createOrder(any(), eq(userId), eq("customer@example.com")))
                    .thenReturn(anOrder());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithOneItem(2)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.totalAmount").value(31.80));
        }

        @Test
        @DisplayName("returns 400 for an order with no items")
        void shouldRejectAnEmptyOrder() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content("{\"items\":[]}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @ParameterizedTest(name = "quantity {0}")
        @ValueSource(ints = {0, -1, -100})
        @DisplayName("returns 400 for a non-positive quantity")
        void shouldRejectNonPositiveQuantities(int quantity) throws Exception {
            // @Valid on the nested list is easy to drop by accident; without it a quantity of
            // -1 would reach pricing and produce a negative total.
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithOneItem(quantity)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @ParameterizedTest(name = "quantity {0}")
        @ValueSource(ints = {101, 1000})
        @DisplayName("returns 400 for a quantity above the maximum")
        void shouldRejectExcessiveQuantities(int quantity) throws Exception {
            // Without a ceiling, a single line item could be used to build an arbitrarily large
            // order — a lever for abuse (stock exhaustion, runaway totals) rather than a normal
            // customer order.
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithOneItem(quantity)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("accepts a quantity at the maximum")
        void shouldAcceptTheMaximumQuantity() throws Exception {
            when(orderService.createOrder(any(), eq(userId), eq("customer@example.com")))
                    .thenReturn(anOrder());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithOneItem(100)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("returns 400 for an order with more than 50 items")
        void shouldRejectTooManyItems() throws Exception {
            // The client dictates quantity per item and item count; unbounded either way lets a
            // single request force pricing calls to catalog-service for as many products as it
            // likes.
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithItemCount(51)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        @DisplayName("accepts an order with exactly 50 items")
        void shouldAcceptTheMaximumItemCount() throws Exception {
            when(orderService.createOrder(any(), eq(userId), eq("customer@example.com")))
                    .thenReturn(anOrder());

            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-User-Id", userId)
                            .header("X-User-Email", "customer@example.com")
                            .content(bodyWithItemCount(50)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("returns 400 when the gateway's identity header is missing")
        void shouldRejectARequestWithoutIdentity() throws Exception {
            // A request that bypassed the gateway is malformed, not a server fault.
            mockMvc.perform(post("/api/v1/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(bodyWithOneItem(1)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}")
    class GetOrder {

        @Test
        @DisplayName("passes the caller's id to the service so ownership can be checked")
        void shouldPassTheCallersIdentity() throws Exception {
            when(orderService.getOrder(orderId, userId)).thenReturn(anOrder());

            mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId.toString()));

            // The controller must never look an order up by id alone.
            verify(orderService).getOrder(orderId, userId);
        }

        @Test
        @DisplayName("returns 404 when the service reports the order as not found")
        void shouldReturn404() throws Exception {
            when(orderService.getOrder(orderId, userId)).thenThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(get("/api/v1/orders/{id}", orderId).header("X-User-Id", userId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 for a malformed order id")
        void shouldRejectAMalformedId() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{id}", "not-a-uuid").header("X-User-Id", userId))
                    .andExpect(status().isBadRequest());

            verify(orderService, never()).getOrder(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{id}/payment")
    class GetOrderPayment {

        @Test
        @DisplayName("returns the payment detail for an order the caller owns")
        void shouldReturnThePaymentDetail() throws Exception {
            PaymentClient.PaymentInfo payment = new PaymentClient.PaymentInfo(
                    UUID.randomUUID(), orderId, userId, new BigDecimal("31.80"), "APPROVED");
            when(orderService.getOrderPayment(orderId, userId)).thenReturn(payment);

            mockMvc.perform(get("/api/v1/orders/{id}/payment", orderId).header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("APPROVED"));
        }

        @Test
        @DisplayName("returns 404 when the order does not exist or is not the caller's")
        void shouldReturn404() throws Exception {
            when(orderService.getOrderPayment(orderId, userId)).thenThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(get("/api/v1/orders/{id}/payment", orderId).header("X-User-Id", userId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 when the gateway's identity header is missing")
        void shouldRejectARequestWithoutIdentity() throws Exception {
            mockMvc.perform(get("/api/v1/orders/{id}/payment", orderId))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/my")
    class ListMyOrders {

        @Test
        @DisplayName("returns the caller's orders as a JSON array")
        void shouldReturnTheCallersOrders() throws Exception {
            when(orderService.getOrdersByUser(userId)).thenReturn(List.of(anOrder()));

            mockMvc.perform(get("/api/v1/orders/my").header("X-User-Id", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].id").value(orderId.toString()));
        }
    }
}
