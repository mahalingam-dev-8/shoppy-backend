package com.shoppy.users;

/** Public view of a user. Unlike the NestJS version, the password hash is never returned. */
public record UserResponse(Long id, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
