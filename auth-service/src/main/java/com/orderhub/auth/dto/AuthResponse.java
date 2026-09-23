package com.orderhub.auth.dto;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        String email,
        String role
) {
    public static AuthResponse of(String token, long expiresIn, String email, String role) {
        return new AuthResponse(token, "Bearer", expiresIn, email, role);
    }
}
