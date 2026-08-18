package com.platform.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the same test-only RSA key pair used across the test suite
 * (src/test/resources/keys) directly, rather than going through Spring context
 * startup - this is a fast, isolated unit test of the signing/verification logic.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        var resourceLoader = new DefaultResourceLoader();

        String privatePem = readPem(resourceLoader, "classpath:keys/private_key.pem", "PRIVATE KEY");
        String publicPem = readPem(resourceLoader, "classpath:keys/public_key.pem", "PUBLIC KEY");

        RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privatePem)));
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicPem)));

        jwtService = new JwtService(privateKey, publicKey);
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlMinutes", 15L);
    }

    @Test
    void generatesAndValidatesATokenRoundTrip() {
        UUID userId = UUID.randomUUID();
        JwtService.AccessToken token = jwtService.generateAccessToken(userId, "someone@platform.local", Set.of("CUSTOMER"));

        Claims claims = jwtService.parseAndValidate(token.token());

        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("email", String.class)).isEqualTo("someone@platform.local");
        assertThat(jwtService.isExpiredOrInvalid(token.token())).isFalse();
    }

    @Test
    void rejectsATamperedToken() {
        JwtService.AccessToken token = jwtService.generateAccessToken(UUID.randomUUID(), "x@platform.local", Set.of("CUSTOMER"));

        // Flip a character in the payload segment to simulate tampering.
        String[] parts = token.token().split("\\.");
        char[] payloadChars = parts[1].toCharArray();
        payloadChars[payloadChars.length / 2] = payloadChars[payloadChars.length / 2] == 'a' ? 'b' : 'a';
        String tampered = parts[0] + "." + new String(payloadChars) + "." + parts[2];

        assertThatThrownBy(() -> jwtService.parseAndValidate(tampered)).isInstanceOf(JwtException.class);
        assertThat(jwtService.isExpiredOrInvalid(tampered)).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlMinutes", 0L);
        JwtService.AccessToken token = jwtService.generateAccessToken(UUID.randomUUID(), "x@platform.local", Set.of("CUSTOMER"));

        // A 0-minute TTL means the token's expiry equals its issue time - it's
        // already expired by the time we check it (or within the same clock tick,
        // so allow one retry-free assertion via a short past-TTL rather than exact 0).
        assertThat(jwtService.isExpiredOrInvalid(token.token())).isTrue();
    }

    private static String readPem(DefaultResourceLoader resourceLoader, String location, String marker) throws Exception {
        var resource = resourceLoader.getResource(location);
        try (var is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return content
                    .replace("-----BEGIN " + marker + "-----", "")
                    .replace("-----END " + marker + "-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
