package com.platform.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetTokenService {

    private static final String KEY_PREFIX = "pwreset:";
    private static final Duration TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(String email) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + token, email, TTL);
        return token;
    }

    public Optional<String> consume(String token) {
        String key = KEY_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        if (email != null) {
            redisTemplate.delete(key);
        }
        return Optional.ofNullable(email);
    }
}
