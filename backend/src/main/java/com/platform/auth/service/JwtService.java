package com.platform.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    @Value("${platform.jwt.access-token-ttl-minutes}")
    private long accessTokenTtlMinutes;

    public JwtService(RSAPrivateKey privateKey, RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public record AccessToken(String token, Instant expiresAt, String jti) {
    }

    public AccessToken generateAccessToken(UUID userId, String email, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenTtlMinutes * 60);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", List.copyOf(roles))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();

        return new AccessToken(token, expiry, jti);
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMinutes * 60;
    }

    /**
     * Parses and validates the token signature/expiry. Throws JwtException (or a subtype)
     * if the token is invalid, expired, or tampered with.
     */
    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isExpiredOrInvalid(String token) {
        try {
            parseAndValidate(token);
            return false;
        } catch (ExpiredJwtException | SignatureException | IllegalArgumentException e) {
            return true;
        }
    }
}
