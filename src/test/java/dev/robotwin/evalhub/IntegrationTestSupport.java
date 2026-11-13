package dev.robotwin.evalhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robotwin.evalhub.audit.AuditLogRepository;
import dev.robotwin.evalhub.evaluation.EvaluationEpisodeRepository;
import dev.robotwin.evalhub.evaluation.EvaluationRunRepository;
import dev.robotwin.evalhub.model.ModelVersionRepository;
import dev.robotwin.evalhub.upload.UploadChunkRepository;
import dev.robotwin.evalhub.upload.UploadSessionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:evalhub-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.evaluation.demo-delay=1ms",
        "app.storage.root=target/test-storage"
})
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected EvaluationEpisodeRepository episodeRepository;

    @Autowired
    protected EvaluationRunRepository evaluationRepository;

    @Autowired
    protected ModelVersionRepository modelRepository;

    @Autowired
    protected UploadChunkRepository chunkRepository;

    @Autowired
    protected UploadSessionRepository uploadRepository;

    @Autowired
    protected AuditLogRepository auditRepository;

    @BeforeEach
    void cleanState() throws Exception {
        episodeRepository.deleteAll();
        evaluationRepository.deleteAll();
        chunkRepository.deleteAll();
        uploadRepository.deleteAll();
        modelRepository.deleteAll();
        auditRepository.deleteAll();
        Path storage = Path.of("target/test-storage");
        if (Files.exists(storage)) {
            try (var paths = Files.walk(storage)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
            }
        }
    }

    protected UUID registerModel() throws Exception {
        String body = """
                {
                  "name": "pi05-bread",
                  "version": "candidate-199",
                  "baseModel": "pi0.5",
                  "artifactUri": "local://models/candidate-199",
                  "artifactSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                }
                """;
        MvcResult result = mockMvc.perform(post("/api/v1/models")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("id").asText());
    }

    protected UUID createEvaluation(UUID modelId) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("modelVersionId", modelId.toString())
                .put("name", "candidate-199 / seed-42")
                .put("inferenceSeed", 42)
                .put("maxSteps", 400)
                .put("numInferenceSteps", 10)
                .set("sceneSeeds", objectMapper.createArrayNode().add(2).add(3).add(4).add(8).add(11))
                .toString();
        MvcResult result = mockMvc.perform(post("/api/v1/evaluations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(readJson(result).get("id").asText());
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    protected String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
