package com.shoppy.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "email should not be empty") String email,
        @NotBlank(message = "password should not be empty") String password) {}
