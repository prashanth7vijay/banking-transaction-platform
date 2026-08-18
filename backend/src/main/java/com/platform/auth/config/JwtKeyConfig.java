package com.platform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtKeyConfig {

    @Value("${platform.jwt.private-key-path}")
    private String privateKeyPath;

    @Value("${platform.jwt.public-key-path}")
    private String publicKeyPath;

    private final ResourceLoader resourceLoader;

    public JwtKeyConfig(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    public RSAPrivateKey jwtPrivateKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPem(privateKeyPath, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    @Bean
    public RSAPublicKey jwtPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pem = readPem(publicKeyPath, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(pem);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String readPem(String location, String marker) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return content
                    .replace("-----BEGIN " + marker + "-----", "")
                    .replace("-----END " + marker + "-----", "")
                    .replaceAll("\\s", "");
        }
    }
}
