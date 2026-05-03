package com.orderhub.payment.integration;

import com.orderhub.payment.dto.PaymentResponse;
import com.orderhub.payment.entity.PaymentStatus;
import com.orderhub.payment.event.OrderCreatedEvent;
import com.orderhub.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void shouldProcessPaymentWhenOrderCreatedEventReceived() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId, userId, "customer@test.com", List.of(), new BigDecimal("99.99"), LocalDateTime.now()
        );

        kafkaTemplate.send("order.created", orderId.toString(), event);

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(paymentRepository.findByOrderId(orderId)).isPresent();
            assertThat(paymentRepository.findByOrderId(orderId).get().getStatus())
                    .isEqualTo(PaymentStatus.APPROVED);
        });
    }

    @Test
    void shouldReturnNotFoundForUnknownPayment() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/payments/" + UUID.randomUUID(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
