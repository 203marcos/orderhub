package com.orderhub.payment.integration;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class PaymentIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    // ConfluentKafkaContainer runs in KRaft mode; the legacy KafkaContainer started a
    // ZooKeeper sidecar, which Kafka 4.0 no longer supports.
    @Container
    @ServiceConnection
    static ConfluentKafkaContainer kafka = new ConfluentKafkaContainer("confluentinc/cp-kafka:7.8.0");

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldProcessPaymentWhenOrderCreatedEventReceived() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        publishOrderCreated(new OrderCreatedEvent(
                orderId, userId, "customer@test.com", List.of(), new BigDecimal("99.99"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
            assertThat(paymentRepository.findByOrderId(orderId).get().getStatus())
                    .isEqualTo(PaymentStatus.APPROVED);
        });
    }

    @Test
    void shouldProcessARedeliveredOrderCreatedEventOnlyOnce() throws Exception {
        UUID orderId = UUID.randomUUID();
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, UUID.randomUUID(), "customer@test.com", List.of(),
                new BigDecimal("77.00"), LocalDateTime.now());

        // Kafka is at-least-once; payments.order_id is unique, so a second insert would throw.
        publishOrderCreated(event);
        publishOrderCreated(event);

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(paymentRepository.findByOrderId(orderId)).isPresent());

        assertThat(paymentRepository.findAll())
                .filteredOn(payment -> payment.getOrderId().equals(orderId))
                .hasSize(1);
    }

    private void publishOrderCreated(OrderCreatedEvent event) throws Exception {
        // Pre-serialized, exactly as the outbox relay puts it on the wire.
        kafkaTemplate.send("order.created", event.orderId().toString(),
                objectMapper.writeValueAsString(event));
    }

    @Test
    void shouldReturnNotFoundForUnknownPayment() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/payments/" + UUID.randomUUID(),
                HttpMethod.GET,
                new HttpEntity<>(userHeaders(UUID.randomUUID())),
                String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldNotExposeAnotherUsersPayment() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();

        publishOrderCreated(new OrderCreatedEvent(
                orderId, owner, "owner@test.com", List.of(), new BigDecimal("42.00"), LocalDateTime.now()));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertThat(paymentRepository.findByOrderId(orderId)).isPresent());

        ResponseEntity<String> asAttacker = restTemplate.exchange(
                "/api/v1/payments/order/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders(UUID.randomUUID())),
                String.class);
        assertThat(asAttacker.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<PaymentResponse> asOwner = restTemplate.exchange(
                "/api/v1/payments/order/" + orderId,
                HttpMethod.GET,
                new HttpEntity<>(userHeaders(owner)),
                PaymentResponse.class);
        assertThat(asOwner.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private static HttpHeaders userHeaders(UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        return headers;
    }
}
