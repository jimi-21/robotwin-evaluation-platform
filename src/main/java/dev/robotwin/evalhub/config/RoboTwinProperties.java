package dev.robotwin.evalhub.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.robotwin")
public record RoboTwinProperties(
        String baseUrl,
        String healthPath,
        String episodePath,
        String apiKey,
        Duration connectTimeout,
        Duration requestTimeout) {

    public RoboTwinProperties {
        baseUrl = baseUrl == null ? "http://localhost:8000" : baseUrl.replaceAll("/+$", "");
        healthPath = healthPath == null ? "/health" : healthPath;
        episodePath = episodePath == null ? "/v1/episodes" : episodePath;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofMinutes(10) : requestTimeout;
    }
}
