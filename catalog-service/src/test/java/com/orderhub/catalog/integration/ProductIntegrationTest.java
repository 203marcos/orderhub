package com.orderhub.catalog.integration;

import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Tag("integration")
class ProductIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    private ProductResponse createProduct(ProductRequest request) {
        return restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class).getBody();
    }

    @Test
    void shouldCreateAndRetrieveProduct() {
        ProductRequest request = new ProductRequest("Burger", "Classic cheese burger", new BigDecimal("25.90"), "food");

        ResponseEntity<ProductResponse> created =
                restTemplate.postForEntity("/api/v1/products", request, ProductResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().name()).isEqualTo("Burger");
        assertThat(created.getBody().price()).isEqualByComparingTo("25.90");
        assertThat(created.getBody().available()).isTrue();

        ResponseEntity<ProductResponse> fetched =
                restTemplate.getForEntity("/api/v1/products/" + created.getBody().id(), ProductResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo("Burger");
    }

    @Test
    void shouldListAvailableProducts() {
        createProduct(new ProductRequest("Pizza", "Margherita", new BigDecimal("35.00"), "food"));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Pizza");
    }

    @Test
    void shouldListProductsByCategory() {
        createProduct(new ProductRequest("Soda", "Cola", new BigDecimal("8.00"), "drinks"));

        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/products/category/drinks", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Soda");
    }

    @Test
    void shouldUpdateProduct() {
        ProductRequest initial = new ProductRequest("Old Name", "desc", new BigDecimal("10.00"), "food");
        ProductResponse created = createProduct(initial);
        assertThat(created).isNotNull();

        ProductRequest update = new ProductRequest("New Name", "new desc", new BigDecimal("15.00"), "food");
        ResponseEntity<ProductResponse> updated = restTemplate.exchange(
                "/api/v1/products/" + created.id(),
                HttpMethod.PUT,
                new HttpEntity<>(update),
                ProductResponse.class
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("New Name");
        assertThat(updated.getBody().price()).isEqualByComparingTo("15.00");
    }

    @Test
    void shouldDeleteProduct() {
        ProductRequest request = new ProductRequest("To Delete", "temp", new BigDecimal("5.00"), "misc");
        ProductResponse created = createProduct(request);
        assertThat(created).isNotNull();

        restTemplate.delete("/api/v1/products/" + created.id());

        ResponseEntity<String> fetched =
                restTemplate.getForEntity("/api/v1/products/" + created.id(), String.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn404ForNonExistentProduct() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/products/00000000-0000-0000-0000-000000000000", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn400ForInvalidProductRequest() {
        ProductRequest invalid = new ProductRequest("", null, new BigDecimal("-1.00"), null);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/products", invalid, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
