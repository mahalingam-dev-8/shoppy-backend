package com.shoppy.orders;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByProductId(Long productId);

    @EntityGraph(attributePaths = "product")
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** Orders for products listed by the given seller (product.user). */
    @EntityGraph(attributePaths = {"product", "user"})
    List<Order> findByProductUserIdOrderByCreatedAtDesc(Long sellerId);
}
