package dev.robotwin.evalhub.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.evaluation")
public record EvaluationProperties(Duration demoDelay) {

    public EvaluationProperties {
        if (demoDelay == null) {
            demoDelay = Duration.ofMillis(50);
        }
    }
}
