package com.shoppy.orders;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.shoppy.products.ProductResponse;
import com.shoppy.users.UserResponse;
import java.time.Instant;

/** Same shape as the Prisma response: the product is always included, the user only for admins. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        Instant createdAt,
        ProductResponse product,
        UserResponse user) {

    static OrderResponse of(Order order, boolean includeUser) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getProduct().getId(),
                order.getCreatedAt(),
                ProductResponse.from(order.getProduct()),
                includeUser ? UserResponse.from(order.getUser()) : null);
    }
}
