package com.orderhub.order.integration;

import com.orderhub.order.client.CatalogClient;
import com.orderhub.order.dto.CreateOrderRequest;
import com.orderhub.order.dto.OrderItemRequest;
import com.orderhub.order.dto.OrderResponse;
import com.orderhub.order.entity.OrderStatus;
import com.orderhub.order.event.PaymentApprovedEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class OrderIntegrationTest {

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

    @MockBean
    CatalogClient catalogClient;

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID productId = UUID.randomUUID();
        when(catalogClient.getProduct(any()))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Burger", new BigDecimal("15.90"), true));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, 2)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UUID.randomUUID().toString());
        headers.set("X-User-Email", "customer@example.com");

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(request, headers),
                OrderResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("31.80"));
    }

    @Test
    void shouldReturnNotFoundForUnknownOrder() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UUID.randomUUID().toString());

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/orders/" + UUID.randomUUID(),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturnBadRequestForEmptyItems() {
        CreateOrderRequest request = new CreateOrderRequest(List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UUID.randomUUID().toString());
        headers.set("X-User-Email", "customer@example.com");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/v1/orders",
                new HttpEntity<>(request, headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnMyOrders() {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(catalogClient.getProduct(any()))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Pizza", new BigDecimal("30.00"), true));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest(productId, 1)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        headers.set("X-User-Email", "customer@example.com");

        restTemplate.postForEntity("/api/v1/orders", new HttpEntity<>(request, headers), OrderResponse.class);

        ResponseEntity<OrderResponse[]> listResponse = restTemplate.exchange(
                "/api/v1/orders/my",
                org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(headers),
                OrderResponse[].class
        );

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotEmpty();
    }

    @Test
    void shouldConfirmOrderWhenPaymentApprovedEventReceived() {
        UUID productId = UUID.randomUUID();
        when(catalogClient.getProduct(any()))
                .thenReturn(new CatalogClient.ProductResponse(productId, "Pizza", new BigDecimal("30.00"), true));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", UUID.randomUUID().toString());
        headers.set("X-User-Email", "customer@example.com");

        CreateOrderRequest request = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1)));
        OrderResponse created = restTemplate.postForEntity(
                "/api/v1/orders", new HttpEntity<>(request, headers), OrderResponse.class).getBody();
        assertThat(created).isNotNull();
        UUID orderId = created.id();

        // Simulate payment-service publishing an approval; the Saga consumer must confirm the order.
        kafkaTemplate.send("payment.approved", orderId.toString(), new PaymentApprovedEvent(
                orderId, UUID.randomUUID(), UUID.randomUUID(), "customer@example.com",
                new BigDecimal("30.00"), LocalDateTime.now()));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            OrderResponse fetched = restTemplate.getForEntity(
                    "/api/v1/orders/" + orderId, OrderResponse.class).getBody();
            assertThat(fetched).isNotNull();
            assertThat(fetched.status()).isEqualTo(OrderStatus.CONFIRMED);
        });
    }
}
