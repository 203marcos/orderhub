package com.orderhub.catalog.repository;

import com.orderhub.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Page<Product> findByAvailableTrue(Pageable pageable);
    Page<Product> findByCategoryIgnoreCase(String category, Pageable pageable);

    /**
     * Reserves {@code quantity} units in a single statement: the availability check
     * ({@code stock >= quantity}) and the write happen atomically in the database, so two
     * concurrent orders for the same product can never both succeed past the point where
     * stock would go negative. A Java read-then-write would race here.
     *
     * @return 1 if the row had enough stock and was updated, 0 otherwise — the caller decides
     *     success from this count, never from a separate read.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity WHERE p.id = :productId AND p.stock >= :quantity")
    int decrementStock(@Param("productId") UUID productId, @Param("quantity") int quantity);

    /** Puts reserved stock back — used both to compensate a partially-reserved order and to
     *  release a reservation on {@code payment.failed} / {@code order.cancelled}. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity WHERE p.id = :productId")
    int incrementStock(@Param("productId") UUID productId, @Param("quantity") int quantity);
}
