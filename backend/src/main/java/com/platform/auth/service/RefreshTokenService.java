package com.platform.auth.service;

import com.platform.auth.domain.RefreshToken;
import com.platform.auth.repository.RefreshTokenRepository;
import com.platform.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${platform.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
    }

    @Transactional
    public IssuedRefreshToken issue(UUID userId) {
        String rawToken = generateRawToken();
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(hash(rawToken));
        entity.setExpiresAt(Instant.now().plusSeconds(refreshTokenTtlDays * 24 * 60 * 60));
        refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(rawToken, entity.getExpiresAt());
    }

    /**
     * Validates the presented raw refresh token, revokes it, and issues a new one
     * (rotation) - this means a stolen-but-already-used refresh token cannot be replayed.
     */
    @Transactional
    public RotationResult rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isActive)
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid or expired"));

        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        IssuedRefreshToken next = issue(existing.getUserId());
        return new RotationResult(existing.getUserId(), next);
    }

    public record RotationResult(UUID userId, IssuedRefreshToken next) {
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(rt -> {
                    rt.setRevokedAt(Instant.now());
                    refreshTokenRepository.save(rt);
                });
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
