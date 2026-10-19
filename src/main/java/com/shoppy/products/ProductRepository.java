package com.shoppy.products;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByIdAsc();

    List<Product> findBySoldFalseOrderByIdAsc();
}
