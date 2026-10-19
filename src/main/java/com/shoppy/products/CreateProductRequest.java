package com.shoppy.products;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * The frontend builds this JSON from a FormData, so {@code price} arrives as a string such as
 * "12.5"; Jackson coerces it to a number, as class-transformer's @Type did in NestJS.
 */
public record CreateProductRequest(
        @NotBlank(message = "name should not be empty") String name,
        @NotBlank(message = "description should not be empty") String description,

        // Stripe rejects charges below its minimum (₹0.50 for INR).
        @NotNull(message = "price must be a number")
        @DecimalMin(value = "0.50", message = "price must be at least 0.50")
        @Digits(integer = 8, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price) {}
