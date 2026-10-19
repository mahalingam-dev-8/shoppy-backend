package com.shoppy.products;

import java.math.BigDecimal;

/** Same JSON field names as the NestJS/Prisma response, including the lowercase {@code userid}. */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Long userid,
        boolean sold,
        String imageUrl) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                // Reading the id of a lazy proxy does not load the user.
                product.getUser().getId(),
                product.isSold(),
                product.getImageUrl());
    }
}
