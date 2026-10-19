package com.shoppy.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Signup body. The user picks USER (customer) or ADMIN (seller). Choosing ADMIN is safe here
 * because in this app it is a seller role: it can list products and see orders for its own
 * products, and grants no access to other users' data.
 */
public record CreateUserRequest(
        @NotBlank(message = "email should not be empty")
        @Email(message = "email must be an email")
        String email,

        // Same rule as class-validator's IsStrongPassword: 8+ chars with lower, upper, digit and symbol.
        @NotBlank(message = "password should not be empty")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "password is not strong enough")
        String password,

        // Optional; defaults to USER.
        @Pattern(regexp = "USER|ADMIN", message = "role must be one of the following values: USER, ADMIN")
        String role) {}
