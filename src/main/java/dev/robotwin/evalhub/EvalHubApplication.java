package dev.robotwin.evalhub;

import dev.robotwin.evalhub.config.EvaluationProperties;
import dev.robotwin.evalhub.config.RoboTwinProperties;
import dev.robotwin.evalhub.config.SecurityProperties;
import dev.robotwin.evalhub.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties({StorageProperties.class, EvaluationProperties.class, SecurityProperties.class,
        RoboTwinProperties.class})
public class EvalHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvalHubApplication.class, args);
    }
}
