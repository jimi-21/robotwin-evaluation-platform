package dev.robotwin.evalhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import dev.robotwin.evalhub.evaluation.EvaluationStatus;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = "admin", roles = "ADMIN")
@TestPropertySource(properties = "app.evaluation.executor=http")
class RoboTwinHttpExecutorIntegrationTest extends IntegrationTestSupport {

    private static final ObjectMapper SERVER_MAPPER = new ObjectMapper();
    private static final AtomicBoolean READY = new AtomicBoolean(true);
    private static final AtomicInteger HEALTH_CALLS = new AtomicInteger();
    private static final List<String> EPISODE_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<String> API_KEYS = new CopyOnWriteArrayList<>();
    private static final HttpServer SERVER = startServer();

    @DynamicPropertySource
    static void runnerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.robotwin.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
        registry.add("app.robotwin.health-path", () -> "/health");
        registry.add("app.robotwin.episode-path", () -> "/v1/episodes");
        registry.add("app.robotwin.api-key", () -> "test-runner-key");
        registry.add("app.robotwin.request-timeout", () -> "5s");
    }

    @AfterAll
    static void stopServer() {
        SERVER.stop(0);
    }

    @BeforeEach
    void resetRunner() {
        READY.set(true);
        HEALTH_CALLS.set(0);
        EPISODE_REQUESTS.clear();
        API_KEYS.clear();
    }

    @Test
    void callsRealHttpContractAndPersistsRunnerResults() throws Exception {
        UUID evaluationId = createEvaluation(registerModel());
        mockMvc.perform(post("/api/v1/evaluations/{id}/start", evaluationId))
                .andExpect(status().isAccepted());

        awaitTerminal(evaluationId, EvaluationStatus.SUCCEEDED);
        assertThat(HEALTH_CALLS).hasValue(1);
        assertThat(EPISODE_REQUESTS).hasSize(5);
        assertThat(API_KEYS).allMatch("test-runner-key"::equals);

        JsonNode firstRequest = SERVER_MAPPER.readTree(EPISODE_REQUESTS.get(0));
        assertThat(firstRequest.get("evaluationId").asText()).isEqualTo(evaluationId.toString());
        assertThat(firstRequest.get("sceneSeed").asInt()).isEqualTo(2);
        assertThat(firstRequest.get("inferenceSeed").asInt()).isEqualTo(42);
        assertThat(firstRequest.get("maxSteps").asInt()).isEqualTo(400);
        assertThat(firstRequest.get("numInferenceSteps").asInt()).isEqualTo(10);

        mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.successCount").value(4))
                .andExpect(jsonPath("$.failureCount").value(1));

        mockMvc.perform(get("/api/v1/evaluations/{id}/episodes", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("FAILURE"))
                .andExpect(jsonPath("$[1].videoUri").value("local://episode-1.mp4"))
                .andExpect(jsonPath("$[1].armTag").value("dual"));

        mockMvc.perform(get("/api/v1/audit-logs/EVALUATION/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[2].detailJson").value(org.hamcrest.Matchers.containsString("http")));
    }

    @Test
    void refusesToRunWhenRunnerIsNotReady() throws Exception {
        READY.set(false);
        UUID evaluationId = createEvaluation(registerModel());
        mockMvc.perform(post("/api/v1/evaluations/{id}/start", evaluationId))
                .andExpect(status().isAccepted());

        awaitTerminal(evaluationId, EvaluationStatus.FAILED);
        assertThat(HEALTH_CALLS).hasValue(1);
        assertThat(EPISODE_REQUESTS).isEmpty();
        assertThat(evaluationRepository.findById(evaluationId).orElseThrow().getErrorMessage())
                .contains("not ready");
    }

    private void awaitTerminal(UUID evaluationId, EvaluationStatus expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        EvaluationStatus current = EvaluationStatus.RUNNING;
        while (current == EvaluationStatus.RUNNING && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = evaluationRepository.findById(evaluationId).orElseThrow().getStatus();
        }
        assertThat(current).isEqualTo(expected);
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/health", exchange -> {
                HEALTH_CALLS.incrementAndGet();
                respond(exchange, 200, "{\"ready\":" + READY.get() + "}");
            });
            server.createContext("/v1/episodes", exchange -> {
                API_KEYS.add(exchange.getRequestHeaders().getFirst("X-API-Key"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                EPISODE_REQUESTS.add(body);
                JsonNode request = SERVER_MAPPER.readTree(body);
                int index = request.get("episodeIndex").asInt();
                String result = index == 0 ? "FAILURE" : "SUCCESS";
                String response = """
                        {
                          "result": "%s",
                          "steps": %d,
                          "jitter": 0.25,
                          "instruction": "HTTP contract test",
                          "videoUri": "local://episode-%d.mp4",
                          "armTag": "dual",
                          "failureReason": %s
                        }
                        """.formatted(result, 200 + index, index,
                        index == 0 ? "\"runner reported failure\"" : "null");
                respond(exchange, 200, response);
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
