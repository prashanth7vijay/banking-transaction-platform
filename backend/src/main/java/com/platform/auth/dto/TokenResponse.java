package com.platform.auth.dto;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
    public static TokenResponse bearer(String accessToken, long expiresInSeconds) {
        return new TokenResponse(accessToken, "Bearer", expiresInSeconds);
    }
}
