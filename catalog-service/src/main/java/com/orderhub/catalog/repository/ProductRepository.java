package com.orderhub.catalog.repository;

import com.orderhub.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByAvailableTrue(Pageable pageable);
    Page<Product> findByCategoryIgnoreCase(String category, Pageable pageable);
}
