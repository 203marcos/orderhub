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
import org.springframework.http.HttpHeaders;
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

    private static final String ROLE_HEADER = "X-User-Role";

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

    private static HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ROLE_HEADER, "ADMIN");
        return headers;
    }

    private ResponseEntity<ProductResponse> post(ProductRequest request) {
        return restTemplate.exchange("/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()), ProductResponse.class);
    }

    private ProductResponse createProduct(ProductRequest request) {
        return post(request).getBody();
    }

    @Test
    void shouldCreateAndRetrieveProduct() {
        ProductRequest request = new ProductRequest(
                "Burger", "Classic cheese burger", new BigDecimal("25.90"), "food", 10);

        ResponseEntity<ProductResponse> created = post(request);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).isNotNull();
        assertThat(created.getBody().name()).isEqualTo("Burger");
        assertThat(created.getBody().price()).isEqualByComparingTo("25.90");
        assertThat(created.getBody().available()).isTrue();
        assertThat(created.getBody().stock()).isEqualTo(10);

        ResponseEntity<ProductResponse> fetched =
                restTemplate.getForEntity("/api/v1/products/" + created.getBody().id(), ProductResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().name()).isEqualTo("Burger");
    }

    @Test
    void shouldListAvailableProducts() {
        createProduct(new ProductRequest("Pizza", "Margherita", new BigDecimal("35.00"), "food", 5));

        ResponseEntity<String> response = restTemplate.getForEntity("/api/v1/products", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Pizza");
    }

    @Test
    void shouldListProductsByCategory() {
        createProduct(new ProductRequest("Soda", "Cola", new BigDecimal("8.00"), "drinks", 20));

        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/products/category/drinks", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Soda");
    }

    @Test
    void shouldUpdateProduct() {
        ProductRequest initial = new ProductRequest("Old Name", "desc", new BigDecimal("10.00"), "food", 3);
        ProductResponse created = createProduct(initial);
        assertThat(created).isNotNull();

        ProductRequest update = new ProductRequest("New Name", "new desc", new BigDecimal("15.00"), "food", 7);
        ResponseEntity<ProductResponse> updated = restTemplate.exchange(
                "/api/v1/products/" + created.id(),
                HttpMethod.PUT,
                new HttpEntity<>(update, adminHeaders()),
                ProductResponse.class
        );

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("New Name");
        assertThat(updated.getBody().price()).isEqualByComparingTo("15.00");
        // Stock is deliberately not writable through PUT — it only moves via the
        // reservation saga, so the created value must survive the update untouched.
        assertThat(updated.getBody().stock()).isEqualTo(3);
    }

    @Test
    void shouldDeleteProduct() {
        ProductRequest request = new ProductRequest("To Delete", "temp", new BigDecimal("5.00"), "misc", 1);
        ProductResponse created = createProduct(request);
        assertThat(created).isNotNull();

        restTemplate.exchange("/api/v1/products/" + created.id(), HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()), Void.class);

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
        ProductRequest invalid = new ProductRequest("", null, new BigDecimal("-1.00"), null, 0);

        ResponseEntity<String> response = restTemplate.exchange("/api/v1/products", HttpMethod.POST,
                new HttpEntity<>(invalid, adminHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn403ForMutationsWithoutTheAdminRole() {
        ProductRequest request = new ProductRequest("Burger", "desc", new BigDecimal("10.00"), "food", 5);

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/products", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
