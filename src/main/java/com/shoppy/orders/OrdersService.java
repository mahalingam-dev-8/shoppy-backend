package com.shoppy.orders;

import com.shoppy.common.ApiException;
import com.shoppy.products.Product;
import com.shoppy.products.ProductRepository;
import com.shoppy.users.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdersService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrdersService(OrderRepository orderRepository, ProductRepository productRepository,
                         UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> forUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(order -> OrderResponse.of(order, false))
                .toList();
    }

    /** Orders for the seller's own products, with the buyer included so the seller can fulfil them. */
    @Transactional(readOnly = true)
    public List<OrderResponse> forSeller(Long sellerId) {
        return orderRepository.findByProductUserIdOrderByCreatedAtDesc(sellerId).stream()
                .map(order -> OrderResponse.of(order, true))
                .toList();
    }

    /**
     * Marks the product sold and records the order, once. Returns false when an order already
     * exists for the product, so Stripe retrying the same webhook does nothing. Two deliveries
     * racing past the check are stopped by the UNIQUE product_id constraint (the caller treats
     * that as a duplicate too).
     */
    @Transactional
    public boolean fulfil(Long productId, Long userId) {
        if (orderRepository.existsByProductId(productId)) {
            return false;
        }
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found for id " + productId));
        product.setSold(true);
        orderRepository.save(new Order(userRepository.getReferenceById(userId), product));
        return true;
    }
}
