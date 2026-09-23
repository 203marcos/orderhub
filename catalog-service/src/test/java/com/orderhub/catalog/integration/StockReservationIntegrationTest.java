package com.orderhub.catalog.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import com.orderhub.catalog.entity.StockReservationStatus;
import com.orderhub.catalog.event.OrderCancelledEvent;
import com.orderhub.catalog.event.OrderCreatedEvent;
import com.orderhub.catalog.event.PaymentFailedEvent;
import com.orderhub.catalog.repository.ProductRepository;
import com.orderhub.catalog.repository.StockReservationRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
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

/**
 * Exercises the compensating-transaction saga end to end against real Postgres and Kafka
 * containers: order.created reserves stock, and either payment.failed or order.cancelled
 * releases it. Requires Docker — not run by default `mvn test` (see the root pom's
 * excludedGroups), only compiled.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class StockReservationIntegrationTest {

    private static final String ROLE_HEADER = "X-User-Role";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    // ConfluentKafkaContainer runs in KRaft mode; the legacy KafkaContainer started a
    // ZooKeeper sidecar, which Kafka 4.0 no longer supports.
    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestRestTemplate restTemplate;
    @Autowired ProductRepository productRepository;
    @Autowired StockReservationRepository stockReservationRepository;

    private UUID createProductWithStock(int stock) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ROLE_HEADER, "ADMIN");
        ProductRequest request = new ProductRequest("Burger", "desc", new BigDecimal("10.00"), "food", stock);

        ResponseEntity<ProductResponse> created = restTemplate.exchange("/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(request, headers), ProductResponse.class);

        return created.getBody().id();
    }

    private void publish(String topic, String key, Object event) throws Exception {
        kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(event));
    }

    @Test
    void shouldReserveStockWhenOrderCreatedArrives() throws Exception {
        UUID productId = createProductWithStock(10);
        UUID orderId = UUID.randomUUID();

        publish("order.created", orderId.toString(), new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com",
                List.of(new OrderCreatedEvent.OrderItemEvent(productId, "Burger", new BigDecimal("10.00"), 3)),
                new BigDecimal("30.00"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(stockReservationRepository.findByOrderId(orderId)).isPresent();
            assertThat(stockReservationRepository.findByOrderId(orderId).get().getStatus())
                    .isEqualTo(StockReservationStatus.RESERVED);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(7);
        });
    }

    @Test
    void shouldReleaseReservationWhenPaymentFailedArrives() throws Exception {
        UUID productId = createProductWithStock(10);
        UUID orderId = UUID.randomUUID();

        publish("order.created", orderId.toString(), new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com",
                List.of(new OrderCreatedEvent.OrderItemEvent(productId, "Burger", new BigDecimal("10.00"), 4)),
                new BigDecimal("40.00"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(6));

        publish("payment.failed", orderId.toString(),
                new PaymentFailedEvent(orderId, UUID.randomUUID(), "Card declined", LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(stockReservationRepository.findByOrderId(orderId).get().getStatus())
                    .isEqualTo(StockReservationStatus.RELEASED);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10);
        });
    }

    @Test
    void shouldReleaseReservationWhenOrderCancelledArrives() throws Exception {
        UUID productId = createProductWithStock(10);
        UUID orderId = UUID.randomUUID();

        publish("order.created", orderId.toString(), new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com",
                List.of(new OrderCreatedEvent.OrderItemEvent(productId, "Burger", new BigDecimal("10.00"), 2)),
                new BigDecimal("20.00"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8));

        publish("order.cancelled", orderId.toString(),
                new OrderCancelledEvent(orderId, UUID.randomUUID(), "Customer request", LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(stockReservationRepository.findByOrderId(orderId).get().getStatus())
                    .isEqualTo(StockReservationStatus.RELEASED);
            assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10);
        });
    }

    @Test
    void shouldNotReserveStockWhenInsufficient() throws Exception {
        UUID productId = createProductWithStock(1);
        UUID orderId = UUID.randomUUID();

        publish("order.created", orderId.toString(), new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com",
                List.of(new OrderCreatedEvent.OrderItemEvent(productId, "Burger", new BigDecimal("10.00"), 5)),
                new BigDecimal("50.00"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertThat(stockReservationRepository.findByOrderId(orderId)).isPresent());

        assertThat(stockReservationRepository.findByOrderId(orderId).get().getStatus())
                .isEqualTo(StockReservationStatus.FAILED);
        // Stock is untouched — the short line never actually applied its decrement.
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(1);
    }
}
