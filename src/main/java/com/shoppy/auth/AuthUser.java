package com.shoppy.auth;

import com.shoppy.users.Role;

/** The authenticated caller, available in controllers through {@code @AuthenticationPrincipal}. */
public record AuthUser(Long id, String email, Role role) {}
