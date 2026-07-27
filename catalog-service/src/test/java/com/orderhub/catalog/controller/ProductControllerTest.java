package com.orderhub.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import com.orderhub.catalog.exception.ProductNotFoundException;
import com.orderhub.catalog.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP edge of catalog-service: status codes, request validation and the JSON contract.
 * No database, no Redis — the service itself is mocked.
 */
@WebMvcTest(ProductController.class)
@DisplayName("ProductController")
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ProductService productService;

    private final UUID productId = UUID.randomUUID();

    private ProductResponse aProduct() {
        return new ProductResponse(productId, "Burger", "Cheese burger",
                new BigDecimal("25.90"), "food", true, LocalDateTime.now(), LocalDateTime.now());
    }

    @Nested
    @DisplayName("POST /api/v1/products")
    class CreateProduct {

        @Test
        @DisplayName("returns 201 with the created product")
        void shouldReturn201() throws Exception {
            when(productService.create(any())).thenReturn(aProduct());

            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ProductRequest(
                                    "Burger", "Cheese burger", new BigDecimal("25.90"), "food"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Burger"))
                    .andExpect(jsonPath("$.available").value(true));
        }

        /**
         * The validation annotations on {@code ProductRequest} are the only thing standing
         * between a client and a product with a blank name or a negative price. Deleting one
         * would not break any other test.
         */
        @ParameterizedTest(name = "name=''{0}'' price={1}")
        @CsvSource({
                "'',      25.90",   // @NotBlank
                "'   ',   25.90",   // @NotBlank treats whitespace as blank
                "Burger, -1.00",    // @Positive
                "Burger,  0.00"     // @Positive excludes zero
        })
        @DisplayName("returns 400 for an invalid product")
        void shouldRejectInvalidProducts(String name, BigDecimal price) throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new ProductRequest(name, "desc", price, "food"))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("returns 400 for a missing price")
        void shouldRejectAMissingPrice() throws Exception {
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Burger\",\"category\":\"food\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 for a malformed JSON body")
        void shouldRejectMalformedJson() throws Exception {
            // Handled by ResponseEntityExceptionHandler; without it this would be a 500.
            mockMvc.perform(post("/api/v1/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": "))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class ReadProducts {

        @Test
        @DisplayName("returns a page of available products")
        void shouldReturnAPage() throws Exception {
            when(productService.findAllAvailable(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(aProduct())));

            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Burger"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 404 for an unknown product")
        void shouldReturn404() throws Exception {
            when(productService.findById(productId)).thenThrow(new ProductNotFoundException(productId));

            mockMvc.perform(get("/api/v1/products/{id}", productId))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("returns 400 for a malformed product id")
        void shouldRejectAMalformedId() throws Exception {
            mockMvc.perform(get("/api/v1/products/{id}", "not-a-uuid"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(productService);
        }

        @Test
        @DisplayName("filters by category")
        void shouldFilterByCategory() throws Exception {
            when(productService.findByCategory(eq("drinks"), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/products/category/{category}", "drinks"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{id}")
    class DeleteProduct {

        @Test
        @DisplayName("returns 404 when the product does not exist")
        void shouldReturn404() throws Exception {
            org.mockito.Mockito.doThrow(new ProductNotFoundException(productId))
                    .when(productService).delete(productId);

            mockMvc.perform(delete("/api/v1/products/{id}", productId))
                    .andExpect(status().isNotFound());
        }
    }
}
