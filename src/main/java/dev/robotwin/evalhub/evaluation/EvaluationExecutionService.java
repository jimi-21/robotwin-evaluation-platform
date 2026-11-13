package dev.robotwin.evalhub.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robotwin.evalhub.audit.AuditService;
import dev.robotwin.evalhub.common.ApiException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationExecutionService {

    private final EvaluationRunRepository runRepository;
    private final EvaluationEpisodeRepository episodeRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EvaluationExecutionService(EvaluationRunRepository runRepository,
            EvaluationEpisodeRepository episodeRepository, AuditService auditService, ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.episodeRepository = episodeRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public EvaluationConfig configFor(UUID evaluationId) {
        EvaluationRun run = getRun(evaluationId);
        if (run.getStatus() != EvaluationStatus.RUNNING) {
            throw ApiException.conflict("EVALUATION_NOT_RUNNING", "Evaluation is not running");
        }
        try {
            return objectMapper.readValue(run.getConfigSnapshot(), EvaluationConfig.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIG_SNAPSHOT",
                    "Stored evaluation config is invalid", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEpisode(UUID evaluationId, int episodeIndex, int sceneSeed, EpisodeResult result, int steps,
            BigDecimal jitter, String instruction, String videoUri, String armTag, String failureReason) {
        EvaluationRun run = getRun(evaluationId);
        if (run.getStatus() != EvaluationStatus.RUNNING) {
            throw ApiException.conflict("EVALUATION_NOT_RUNNING", "Cannot append results to a non-running evaluation");
        }
        episodeRepository.save(new EvaluationEpisode(run, episodeIndex, sceneSeed, result, steps, jitter,
                instruction, videoUri, armTag, failureReason));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID evaluationId, String executorName) {
        EvaluationRun run = getRun(evaluationId);
        if (run.getStatus() != EvaluationStatus.RUNNING) {
            throw ApiException.conflict("EVALUATION_NOT_RUNNING", "Evaluation is not running");
        }
        List<EvaluationEpisode> episodes = episodeRepository.findByEvaluationRunIdOrderByEpisodeIndexAsc(evaluationId);
        EvaluationConfig config = readConfig(run);
        if (episodes.size() != config.sceneSeeds().size()) {
            throw new IllegalStateException("Evaluation result count does not match the scene plan");
        }
        applyMetrics(run, episodes);
        run.succeed();
        auditService.record("EVALUATION", evaluationId, "EVALUATION_SUCCEEDED", Map.of(
                "attempt", run.getAttempt(), "episodes", episodes.size(), "executor", executorName));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID evaluationId, String message) {
        EvaluationRun run = getRun(evaluationId);
        if (run.getStatus() == EvaluationStatus.RUNNING) {
            run.fail(message == null || message.isBlank() ? "Evaluation execution failed" : message);
            auditService.record("EVALUATION", evaluationId, "EVALUATION_FAILED",
                    Map.of("attempt", run.getAttempt()));
        }
    }

    private EvaluationRun getRun(UUID id) {
        return runRepository.findById(id).orElseThrow(() -> ApiException.notFound("Evaluation", id));
    }

    private EvaluationConfig readConfig(EvaluationRun run) {
        try {
            return objectMapper.readValue(run.getConfigSnapshot(), EvaluationConfig.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored evaluation config is invalid", exception);
        }
    }

    private void applyMetrics(EvaluationRun run, List<EvaluationEpisode> episodes) {
        EvaluationMetrics metrics = EvaluationMetrics.from(episodes);
        run.setMetrics(metrics.successCount(), metrics.failureCount(), metrics.timeoutCount(), metrics.successRate(),
                metrics.meanSuccessSteps(), metrics.meanJitter());
    }
}
