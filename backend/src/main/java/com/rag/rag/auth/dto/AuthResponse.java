package com.rag.rag.auth.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", user);
    }
}
