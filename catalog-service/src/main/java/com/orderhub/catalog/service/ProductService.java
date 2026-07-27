package com.orderhub.catalog.service;

import com.orderhub.catalog.dto.ProductRequest;
import com.orderhub.catalog.dto.ProductResponse;
import com.orderhub.catalog.config.RedisConfig;
import com.orderhub.catalog.entity.Product;
import com.orderhub.catalog.exception.ProductNotFoundException;
import com.orderhub.catalog.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductResponse> findAllAvailable(Pageable pageable) {
        return productRepository.findByAvailableTrue(pageable).map(ProductResponse::from);
    }

    @Cacheable(value = RedisConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse findById(UUID id) {
        return ProductResponse.from(findProductOrThrow(id));
    }

    public Page<ProductResponse> findByCategory(String category, Pageable pageable) {
        return productRepository.findByCategoryIgnoreCase(category, pageable).map(ProductResponse::from);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Product product = new Product(
                request.name(), request.description(), request.price(), request.category(), request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = RedisConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());
        product.setStock(request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    @CacheEvict(value = RedisConfig.PRODUCTS_CACHE, key = "#id")
    public void delete(UUID id) {
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }

    private Product findProductOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}
