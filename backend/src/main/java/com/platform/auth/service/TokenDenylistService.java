package com.platform.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenDenylistService {

    private static final String KEY_PREFIX = "jwt:denylist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * Denies a specific access token (by jti) until it would have naturally expired -
     * no point keeping it in Redis any longer than that.
     */
    public void deny(String jti, Instant tokenExpiresAt) {
        Duration ttl = Duration.between(Instant.now(), tokenExpiresAt);
        if (ttl.isNegative()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
    }

    public boolean isDenied(String jti) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}
