package dev.robotwin.evalhub.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        String adminUsername,
        String adminPassword,
        String viewerUsername,
        String viewerPassword) {

    public SecurityProperties {
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofHours(2);
        }
    }

    public SecretKey secretKey() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
