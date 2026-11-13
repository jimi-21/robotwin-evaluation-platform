package dev.robotwin.evalhub.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robotwin.evalhub.audit.AuditService;
import dev.robotwin.evalhub.common.ApiException;
import dev.robotwin.evalhub.model.ModelVersion;
import dev.robotwin.evalhub.model.ModelVersionResponse;
import dev.robotwin.evalhub.model.ModelVersionService;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EvaluationService {

    private final EvaluationRunRepository runRepository;
    private final EvaluationEpisodeRepository episodeRepository;
    private final ModelVersionService modelService;
    private final EvaluationDispatcher dispatcher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public EvaluationService(EvaluationRunRepository runRepository, EvaluationEpisodeRepository episodeRepository,
            ModelVersionService modelService, EvaluationDispatcher dispatcher, AuditService auditService,
            ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.episodeRepository = episodeRepository;
        this.modelService = modelService;
        this.dispatcher = dispatcher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EvaluationResponse create(CreateEvaluationRequest request) {
        Set<Integer> distinctScenes = new HashSet<>(request.sceneSeeds());
        if (distinctScenes.size() != request.sceneSeeds().size()) {
            throw ApiException.badRequest("DUPLICATE_SCENE", "sceneSeeds must be unique");
        }
        ModelVersion model = modelService.get(request.modelVersionId());
        EvaluationConfig config = new EvaluationConfig(request.sceneSeeds(), request.inferenceSeed(),
                request.maxSteps(), request.numInferenceSteps(), request.parameters());
        EvaluationRun run = new EvaluationRun(model, request.name(), writeJson(config), request.maxAttempts());
        runRepository.save(run);
        auditService.record("EVALUATION", run.getId(), "EVALUATION_CREATED", Map.of(
                "modelVersionId", model.getId(), "sceneCount", request.sceneSeeds().size()));
        return toResponse(run);
    }

    @Transactional
    public EvaluationResponse start(UUID id) {
        EvaluationRun run = getRun(id);
        if (run.getStatus() != EvaluationStatus.PENDING && run.getStatus() != EvaluationStatus.FAILED) {
            throw ApiException.conflict("INVALID_EVALUATION_TRANSITION",
                    "Only PENDING or FAILED evaluations can be started");
        }
        if (run.getAttempt() >= run.getMaxAttempts()) {
            throw ApiException.conflict("EVALUATION_ATTEMPTS_EXHAUSTED", "No evaluation attempts remain");
        }
        episodeRepository.deleteByEvaluationRunId(id);
        run.start();
        auditService.record("EVALUATION", id, "EVALUATION_STARTED", Map.of("attempt", run.getAttempt()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatcher.dispatch(id);
            }
        });
        return toResponse(run);
    }

    @Transactional
    public EvaluationResponse importResults(UUID id, ScoreAuditImportRequest request) {
        EvaluationRun run = getRun(id);
        if (run.getStatus() != EvaluationStatus.PENDING && run.getStatus() != EvaluationStatus.FAILED) {
            throw ApiException.conflict("INVALID_EVALUATION_TRANSITION",
                    "Results can only be imported into PENDING or FAILED evaluations");
        }
        EvaluationConfig config = readConfig(run);
        validateImport(config, request);
        episodeRepository.deleteByEvaluationRunId(id);
        run.start();
        int index = 0;
        for (ScoreAuditImportRequest.EpisodeImportRequest episode : request.episodeRecords()) {
            EpisodeResult result = episode.success() ? EpisodeResult.SUCCESS : EpisodeResult.FAILURE;
            episodeRepository.save(new EvaluationEpisode(run, episode.episode(), episode.seed(), result,
                    episode.steps(), episode.jitter(), episode.instruction(), episode.video(), episode.armTag(),
                    episode.success() ? null : "Imported unsuccessful episode"));
            index++;
        }
        List<EvaluationEpisode> episodes = episodeRepository.findByEvaluationRunIdOrderByEpisodeIndexAsc(id);
        EvaluationMetrics metrics = EvaluationMetrics.from(episodes);
        run.setMetrics(metrics.successCount(), metrics.failureCount(), metrics.timeoutCount(), metrics.successRate(),
                metrics.meanSuccessSteps(), metrics.meanJitter());
        run.setScoreSnapshot(writeJson(request.score()), request.score().total());
        run.succeed();
        auditService.record("EVALUATION", id, "RESULTS_IMPORTED", Map.of("episodes", index, "source", "score_audit"));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public EvaluationResponse detail(UUID id) {
        return toResponse(getRun(id));
    }

    @Transactional(readOnly = true)
    public List<EvaluationResponse> list() {
        return runRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EvaluationEpisodeResponse> episodes(UUID id) {
        if (!runRepository.existsById(id)) {
            throw ApiException.notFound("Evaluation", id);
        }
        return episodeRepository.findByEvaluationRunIdOrderByEpisodeIndexAsc(id).stream()
                .map(EvaluationEpisodeResponse::from).toList();
    }

    private EvaluationRun getRun(UUID id) {
        return runRepository.findById(id).orElseThrow(() -> ApiException.notFound("Evaluation", id));
    }

    private void validateImport(EvaluationConfig config, ScoreAuditImportRequest request) {
        if (request.episodeRecords().size() != config.sceneSeeds().size()) {
            throw ApiException.badRequest("RESULT_COUNT_MISMATCH", "Episode count must match the configured scene plan");
        }
        Set<Integer> actualSeeds = new HashSet<>();
        Set<Integer> actualIndexes = new HashSet<>();
        request.episodeRecords().forEach(episode -> {
            actualSeeds.add(episode.seed());
            actualIndexes.add(episode.episode());
        });
        if (!actualSeeds.equals(new HashSet<>(config.sceneSeeds())) || actualSeeds.size() != request.episodeRecords().size()) {
            throw ApiException.badRequest("RESULT_SCENE_MISMATCH", "Imported seeds must exactly match the configured scenes");
        }
        if (actualIndexes.size() != request.episodeRecords().size()) {
            throw ApiException.badRequest("DUPLICATE_EPISODE", "Episode indexes must be unique");
        }
    }

    private EvaluationResponse toResponse(EvaluationRun run) {
        return new EvaluationResponse(run.getId(), ModelVersionResponse.from(run.getModelVersion()), run.getName(),
                run.getStatus(), readTree(run.getConfigSnapshot()), readTree(run.getScoreSnapshot()), run.getAttempt(),
                run.getMaxAttempts(), run.getSuccessCount(), run.getFailureCount(), run.getTimeoutCount(),
                run.getSuccessRate(), run.getMeanSuccessSteps(), run.getMeanJitter(), run.getTotalScore(),
                run.getErrorMessage(), run.getCreatedAt(), run.getStartedAt(), run.getFinishedAt(), run.getVersion());
    }

    private EvaluationConfig readConfig(EvaluationRun run) {
        try {
            return objectMapper.readValue(run.getConfigSnapshot(), EvaluationConfig.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_CONFIG_SNAPSHOT",
                    "Stored evaluation config is invalid", exception);
        }
    }

    private JsonNode readTree(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVALID_STORED_JSON",
                    "Stored JSON document is invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON_SERIALIZATION_FAILED",
                    "Could not serialize JSON document", exception);
        }
    }
}
