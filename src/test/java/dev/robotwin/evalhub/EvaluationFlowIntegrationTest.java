package dev.robotwin.evalhub;

import dev.robotwin.evalhub.evaluation.EvaluationStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = "admin", roles = "ADMIN")
class EvaluationFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    void runsDemoEvaluationWithImmutableConfigEpisodesAndAuditTrail() throws Exception {
        UUID modelId = registerModel();
        UUID evaluationId = createEvaluation(modelId);

        mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.configSnapshot.sceneSeeds.length()").value(5))
                .andExpect(jsonPath("$.configSnapshot.maxSteps").value(400))
                .andExpect(jsonPath("$.attempt").value(0));

        mockMvc.perform(post("/api/v1/evaluations/{id}/start", evaluationId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.attempt").value(1));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        EvaluationStatus current = EvaluationStatus.RUNNING;
        while (current == EvaluationStatus.RUNNING && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
            current = evaluationRepository.findById(evaluationId).orElseThrow().getStatus();
        }
        assertThat(current).isEqualTo(EvaluationStatus.SUCCEEDED);

        mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(2))
                .andExpect(jsonPath("$.timeoutCount").value(0))
                .andExpect(jsonPath("$.successRate").value(0.6))
                .andExpect(jsonPath("$.totalScore").doesNotExist());

        mockMvc.perform(get("/api/v1/evaluations/{id}/episodes", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].sceneSeed").value(2))
                .andExpect(jsonPath("$[0].result").value("FAILURE"))
                .andExpect(jsonPath("$[1].instruction").value(org.hamcrest.Matchers.containsString("Synthetic")));

        mockMvc.perform(get("/api/v1/audit-logs/EVALUATION/{id}", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", containsInAnyOrder(
                        "EVALUATION_CREATED", "EVALUATION_STARTED", "EVALUATION_SUCCEEDED")));
    }

    @Test
    void importsRealScoreAuditAndDerivesMetricsFromEpisodes() throws Exception {
        UUID evaluationId = createEvaluation(registerModel());
        byte[] audit = Files.readAllBytes(Path.of("examples/score_audit.json"));

        mockMvc.perform(post("/api/v1/evaluations/{id}/results", evaluationId)
                        .contentType(MediaType.APPLICATION_JSON).content(audit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.successCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(2))
                .andExpect(jsonPath("$.meanSuccessSteps").value(223.7))
                .andExpect(jsonPath("$.meanJitter").value(0.297944))
                .andExpect(jsonPath("$.totalScore").value(79.7))
                .andExpect(jsonPath("$.scoreSnapshot.segment_stats[0].name").value("clean"));

        mockMvc.perform(get("/api/v1/evaluations/{id}/episodes", evaluationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].videoUri").value("episode0.mp4"))
                .andExpect(jsonPath("$[4].steps").value(210));
    }

    @Test
    void rejectsResultImportWhenScenePlanDoesNotMatch() throws Exception {
        UUID evaluationId = createEvaluation(registerModel());
        String audit = Files.readString(Path.of("examples/score_audit.json"))
                .replaceFirst("\"seed\": 2", "\"seed\": 99");

        mockMvc.perform(post("/api/v1/evaluations/{id}/results", evaluationId)
                        .contentType(MediaType.APPLICATION_JSON).content(audit))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_SCENE_MISMATCH"));
    }

    @Test
    void rejectsDuplicateScenesBeforeCreatingEvaluation() throws Exception {
        UUID modelId = registerModel();
        String body = """
                {
                  "modelVersionId": "%s",
                  "name": "invalid-plan",
                  "sceneSeeds": [2, 2, 3]
                }
                """.formatted(modelId);

        mockMvc.perform(post("/api/v1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SCENE"));
    }
}
