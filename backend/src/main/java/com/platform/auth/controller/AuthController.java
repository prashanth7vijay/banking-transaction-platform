package com.platform.auth.controller;

import com.platform.auth.dto.*;
import com.platform.auth.security.AuthenticatedUser;
import com.platform.auth.service.AuthService;
import com.platform.auth.service.JwtService;
import com.platform.auth.util.CookieUtil;
import com.platform.users.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;
    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.email(), request.password());
        return withRefreshCookie(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(HttpServletRequest request) {
        String rawRefreshToken = extractRefreshCookie(request);
        AuthService.LoginResult result = authService.refresh(rawRefreshToken);
        return withRefreshCookie(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String rawRefreshToken = extractRefreshCookie(request);

        String jti = null;
        Instant expiry = null;
        java.util.UUID userId = null;
        String email = null;

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser) {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = jwtService.parseAndValidate(header.substring(7));
                    jti = claims.getId();
                    expiry = claims.getExpiration().toInstant();
                    userId = java.util.UUID.fromString(claims.getSubject());
                    email = claims.get("email", String.class);
                } catch (Exception ignored) {
                    // token already invalid - nothing to deny
                }
            }
        }

        authService.logout(rawRefreshToken, jti, expiry, userId, email);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, CookieUtil.buildExpiredRefreshCookie().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<TokenResponse> withRefreshCookie(AuthService.LoginResult result) {
        var cookie = CookieUtil.buildRefreshCookie(
                result.refreshToken().rawToken(),
                Duration.between(Instant.now(), result.refreshToken().expiresAt())
        );
        TokenResponse body = TokenResponse.bearer(
                result.accessToken().token(),
                jwtService.getAccessTokenTtlSeconds()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(body);
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, CookieUtil.REFRESH_COOKIE_NAME);
        if (cookie == null || cookie.getValue().isBlank()) {
            throw new com.platform.shared.exception.UnauthorizedException("Refresh token cookie is missing");
        }
        return cookie.getValue();
    }
}