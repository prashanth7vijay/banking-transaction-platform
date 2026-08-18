package com.platform.auth.util;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtil {

    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    private CookieUtil() {
    }

    public static ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false) // set true once served over HTTPS
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    public static ResponseCookie buildExpiredRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
    }
}
