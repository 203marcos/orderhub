package com.orderhub.catalog.controller;

import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import com.orderhub.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products", description = "Product catalog endpoints")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List available products (paginated)")
    public ResponseEntity<Page<ProductResponse>> listAvailable(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.findAllAvailable(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "List products by category")
    public ResponseEntity<Page<ProductResponse>> getByCategory(
            @PathVariable String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.findByCategory(category, pageable));
    }

    @PostMapping
    @Operation(summary = "Create a new product (ADMIN)")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product (ADMIN)")
    public ResponseEntity<ProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a product (ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
