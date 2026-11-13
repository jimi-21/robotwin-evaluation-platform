package dev.robotwin.evalhub.evaluation;

import dev.robotwin.evalhub.config.EvaluationProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.evaluation", name = "executor", havingValue = "demo", matchIfMissing = true)
public class DemoEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(DemoEvaluationExecutor.class);

    private final EvaluationExecutionService executionService;
    private final EvaluationProperties properties;

    public DemoEvaluationExecutor(EvaluationExecutionService executionService, EvaluationProperties properties) {
        this.executionService = executionService;
        this.properties = properties;
    }

    @Override
    public void execute(UUID evaluationId) {
        try {
            EvaluationConfig config = executionService.configFor(evaluationId);
            int episodeIndex = 0;
            for (Integer sceneSeed : config.sceneSeeds()) {
                pause();
                DemoResult demo = resultFor(sceneSeed, config.inferenceSeed(), config.maxSteps());
                executionService.recordEpisode(evaluationId, episodeIndex++, sceneSeed, demo.result(), demo.steps(),
                        demo.jitter(), "Synthetic demonstration scene; not a RoboTwin official result",
                        null, "demo", demo.failureReason());
            }
            executionService.complete(evaluationId, "demo");
        } catch (Exception exception) {
            log.warn("Evaluation {} failed", evaluationId, exception);
            executionService.fail(evaluationId, exception.getMessage());
        }
    }

    private DemoResult resultFor(int sceneSeed, int inferenceSeed, int maxSteps) {
        return switch (sceneSeed) {
            case 2, 4 -> new DemoResult(EpisodeResult.FAILURE, maxSteps, jitter(sceneSeed),
                    "Synthetic task failure for demonstration");
            case 3 -> successfulOrTimeout(232, maxSteps, jitter(sceneSeed));
            case 8 -> successfulOrTimeout(229, maxSteps, jitter(sceneSeed));
            case 11 -> successfulOrTimeout(210, maxSteps, jitter(sceneSeed));
            default -> {
                int steps = 180 + Math.floorMod(sceneSeed * 17 + inferenceSeed, 120);
                if (Math.floorMod(sceneSeed + inferenceSeed, 3) == 0) {
                    yield new DemoResult(EpisodeResult.FAILURE, maxSteps, jitter(sceneSeed),
                            "Synthetic task failure for demonstration");
                }
                yield successfulOrTimeout(steps, maxSteps, jitter(sceneSeed));
            }
        };
    }

    private DemoResult successfulOrTimeout(int steps, int maxSteps, BigDecimal jitter) {
        if (steps > maxSteps) {
            return new DemoResult(EpisodeResult.TIMEOUT, maxSteps, jitter, "Exceeded configured maxSteps");
        }
        return new DemoResult(EpisodeResult.SUCCESS, steps, jitter, null);
    }

    private BigDecimal jitter(int seed) {
        return BigDecimal.valueOf(200000 + Math.floorMod(seed * 137, 250000))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }

    private void pause() {
        try {
            Thread.sleep(properties.demoDelay().toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Evaluation execution interrupted", exception);
        }
    }

    private record DemoResult(EpisodeResult result, int steps, BigDecimal jitter, String failureReason) {
    }
}
