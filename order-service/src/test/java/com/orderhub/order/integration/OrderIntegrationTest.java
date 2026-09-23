package com.orderhub.order.integration;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderItemRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.PaymentApprovedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class OrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // ConfluentKafkaContainer runs in KRaft mode; the legacy KafkaContainer started a
    // ZooKeeper sidecar, which Kafka 4.0 no longer supports.
    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @MockitoBean
    CatalogClient catalogClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ObjectMapper objectMapper;

    // ---------------------------------------------------------------- helpers

    private void givenCatalogOffers(UUID productId, String name, String price) {
        when(catalogClient.getProduct(any()))
                .thenReturn(new CatalogClient.ProductResponse(productId, name, new BigDecimal(price), true));
    }

    private HttpHeaders headersFor(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        return headers;
    }

    private HttpHeaders headersFor(UUID userId, String email) {
        HttpHeaders headers = headersFor(userId);
        headers.set("X-User-Email", email);
        return headers;
    }

    private CreateOrderRequest orderRequestFor(UUID productId, int quantity) {
        return new CreateOrderRequest(List.of(new OrderItemRequest(productId, quantity)));
    }

    private ResponseEntity<OrderResponse> postOrder(CreateOrderRequest request, HttpHeaders headers) {
        return restTemplate.postForEntity("/api/v1/orders", new HttpEntity<>(request, headers), OrderResponse.class);
    }

    private ResponseEntity<String> postOrderRaw(CreateOrderRequest request, HttpHeaders headers) {
        return restTemplate.postForEntity("/api/v1/orders", new HttpEntity<>(request, headers), String.class);
    }

    private ResponseEntity<OrderResponse> getOrder(UUID orderId, HttpHeaders headers) {
        return restTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), OrderResponse.class);
    }

    private ResponseEntity<String> getOrderRaw(UUID orderId, HttpHeaders headers) {
        return restTemplate.exchange(
                "/api/v1/orders/" + orderId, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID productId = UUID.randomUUID();
        givenCatalogOffers(productId, "Burger", "15.90");

        ResponseEntity<OrderResponse> response = postOrder(
                orderRequestFor(productId, 2), headersFor(UUID.randomUUID(), "customer@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("31.80"));
    }

    @Test
    void shouldReturnNotFoundForUnknownOrder() {
        ResponseEntity<String> response = getOrderRaw(UUID.randomUUID(), headersFor(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldNotExposeAnotherUsersOrder() {
        UUID productId = UUID.randomUUID();
        givenCatalogOffers(productId, "Burger", "15.90");

        OrderResponse created = postOrder(
                orderRequestFor(productId, 1), headersFor(UUID.randomUUID(), "owner@example.com")).getBody();
        assertThat(created).isNotNull();

        ResponseEntity<String> response = getOrderRaw(created.id(), headersFor(UUID.randomUUID()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnBadRequestForEmptyItems() {
        ResponseEntity<String> response = postOrderRaw(
                new CreateOrderRequest(List.of()), headersFor(UUID.randomUUID(), "customer@example.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnMyOrders() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        givenCatalogOffers(productId, "Pizza", "30.00");
        HttpHeaders headers = headersFor(userId, "customer@example.com");

        postOrder(orderRequestFor(productId, 1), headers);

        ResponseEntity<OrderResponse[]> listResponse = restTemplate.exchange(
                "/api/v1/orders/my", HttpMethod.GET, new HttpEntity<>(headers), OrderResponse[].class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();
    }

    @Test
    void shouldConfirmOrderWhenPaymentApprovedEventReceived() throws Exception {
        UUID productId = UUID.randomUUID();
        givenCatalogOffers(productId, "Pizza", "30.00");
        HttpHeaders headers = headersFor(UUID.randomUUID(), "customer@example.com");

        OrderResponse created = postOrder(orderRequestFor(productId, 1), headers).getBody();
        assertThat(created).isNotNull();
        UUID orderId = created.id();

        // Simulate payment-service publishing an approval; the Saga consumer must confirm the
        // order. Payloads go on the wire pre-serialized, exactly as the outbox relay sends them.
        kafkaTemplate.send("payment.approved", orderId.toString(),
                objectMapper.writeValueAsString(new PaymentApprovedEvent(
                        orderId, UUID.randomUUID(), UUID.randomUUID(), "customer@example.com",
                        new BigDecimal("30.00"), LocalDateTime.now())));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            OrderResponse fetched = getOrder(orderId, headers).getBody();
            assertThat(fetched).isNotNull();
            assertThat(fetched.status()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }
}
