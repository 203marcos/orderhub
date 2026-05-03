package com.orderhub.catalog.service;

import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import com.orderhub.catalog.entity.Product;
import com.orderhub.catalog.exception.ProductNotFoundException;
import com.orderhub.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        product = new Product("Burger", "Cheese burger", new BigDecimal("25.90"), "food");
        ReflectionTestUtils.setField(product, "id", productId);
        ReflectionTestUtils.setField(product, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(product, "updatedAt", LocalDateTime.now());
    }

    @Test
    void shouldReturnProductById() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse response = productService.findById(productId);

        assertThat(response.id()).isEqualTo(productId);
        assertThat(response.name()).isEqualTo("Burger");
        assertThat(response.price()).isEqualByComparingTo("25.90");
        verify(productRepository).findById(productId);
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(productId))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(productId.toString());
    }

    @Test
    void shouldCreateProduct() {
        ProductRequest request = new ProductRequest("Burger", "Cheese burger", new BigDecimal("25.90"), "food");
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.create(request);

        assertThat(response.name()).isEqualTo("Burger");
        assertThat(response.category()).isEqualTo("food");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldUpdateProduct() {
        ProductRequest request = new ProductRequest("Updated Burger", "Updated desc", new BigDecimal("29.90"), "food");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        ProductResponse response = productService.update(productId, request);

        assertThat(response).isNotNull();
        verify(productRepository).findById(productId);
        verify(productRepository).save(product);
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentProduct() {
        ProductRequest request = new ProductRequest("Burger", null, new BigDecimal("25.90"), null);
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(productId, request))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldDeleteProduct() {
        when(productRepository.existsById(productId)).thenReturn(true);

        productService.delete(productId);

        verify(productRepository).deleteById(productId);
    }

    @Test
    void shouldThrowWhenDeletingNonExistentProduct() {
        when(productRepository.existsById(productId)).thenReturn(false);

        assertThatThrownBy(() -> productService.delete(productId))
                .isInstanceOf(ProductNotFoundException.class);
        verify(productRepository, never()).deleteById(any());
    }

    @Test
    void shouldReturnPageOfAvailableProducts() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByAvailableTrue(pageable)).thenReturn(page);

        Page<ProductResponse> result = productService.findAllAvailable(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Burger");
    }

    @Test
    void shouldReturnPageOfProductsByCategory() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findByCategoryIgnoreCase("food", pageable)).thenReturn(page);

        Page<ProductResponse> result = productService.findByCategory("food", pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).category()).isEqualTo("food");
    }
}
