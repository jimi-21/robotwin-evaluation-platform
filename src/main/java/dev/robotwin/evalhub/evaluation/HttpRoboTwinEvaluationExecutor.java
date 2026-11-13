package dev.robotwin.evalhub.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robotwin.evalhub.config.RoboTwinProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.evaluation", name = "executor", havingValue = "http")
public class HttpRoboTwinEvaluationExecutor implements EvaluationExecutor {

    private static final Logger log = LoggerFactory.getLogger(HttpRoboTwinEvaluationExecutor.class);

    private final EvaluationExecutionService executionService;
    private final RoboTwinProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public HttpRoboTwinEvaluationExecutor(EvaluationExecutionService executionService,
            RoboTwinProperties properties, ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    @Override
    public void execute(UUID evaluationId) {
        try {
            preflight();
            EvaluationConfig config = executionService.configFor(evaluationId);
            int episodeIndex = 0;
            for (Integer sceneSeed : config.sceneSeeds()) {
                JsonNode result = executeEpisode(evaluationId, episodeIndex, sceneSeed, config);
                executionService.recordEpisode(evaluationId, episodeIndex, sceneSeed,
                        result(result), requiredSteps(result), optionalDecimal(result, "jitter"),
                        optionalText(result, "instruction"), optionalText(result, "videoUri"),
                        optionalText(result, "armTag"), optionalText(result, "failureReason"));
                episodeIndex++;
            }
            executionService.complete(evaluationId, "http");
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("RoboTwin HTTP evaluation {} failed", evaluationId, exception);
            executionService.fail(evaluationId, exception.getMessage());
        }
    }

    private void preflight() throws Exception {
        HttpResponse<String> response = send(request(properties.healthPath())
                .GET()
                .build());
        requireSuccess(response, "RoboTwin health check");
        String body = response.body();
        if (body != null && !body.isBlank()) {
            JsonNode json = objectMapper.readTree(body);
            if (json.isObject() && json.has("ready") && !json.path("ready").asBoolean()) {
                throw new IllegalStateException("RoboTwin runner is not ready");
            }
        }
    }

    private JsonNode executeEpisode(UUID evaluationId, int episodeIndex, int sceneSeed, EvaluationConfig config)
            throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("evaluationId", evaluationId.toString());
        request.put("episodeIndex", episodeIndex);
        request.put("sceneSeed", sceneSeed);
        request.put("inferenceSeed", config.inferenceSeed());
        request.put("maxSteps", config.maxSteps());
        request.put("numInferenceSteps", config.numInferenceSteps());
        request.put("parameters", config.parameters());
        HttpRequest httpRequest = request(properties.episodePath())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                .build();
        HttpResponse<String> response = send(httpRequest);
        requireSuccess(response, "RoboTwin episode " + episodeIndex);
        JsonNode result = objectMapper.readTree(response.body());
        if (!result.isObject()) {
            throw new IllegalStateException("RoboTwin episode response must be a JSON object");
        }
        return result;
    }

    private HttpRequest.Builder request(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl() + normalizedPath))
                .timeout(properties.requestTimeout());
        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.header("X-API-Key", properties.apiKey());
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void requireSuccess(HttpResponse<String> response, String operation) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(operation + " returned HTTP " + response.statusCode());
        }
    }

    private EpisodeResult result(JsonNode result) {
        try {
            return EpisodeResult.valueOf(result.path("result").asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid episode result; expected SUCCESS, FAILURE, or TIMEOUT", exception);
        }
    }

    private int requiredSteps(JsonNode result) {
        int steps = result.path("steps").asInt(0);
        if (steps < 1) {
            throw new IllegalStateException("Episode steps must be positive");
        }
        return steps;
    }

    private BigDecimal optionalDecimal(JsonNode result, String field) {
        return result.hasNonNull(field) ? result.path(field).decimalValue() : null;
    }

    private String optionalText(JsonNode result, String field) {
        return result.hasNonNull(field) ? result.path(field).asText() : null;
    }
}
