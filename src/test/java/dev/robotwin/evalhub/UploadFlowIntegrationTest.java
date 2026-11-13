package dev.robotwin.evalhub;

import dev.robotwin.evalhub.upload.UploadProgressResponse;
import dev.robotwin.evalhub.upload.UploadService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser(username = "admin", roles = "ADMIN")
class UploadFlowIntegrationTest extends IntegrationTestSupport {

    private static final int CHUNK_SIZE = 64 * 1024;

    @Autowired
    private UploadService uploadService;

    @Test
    void serializesConcurrentDuplicateChunksWithDatabaseRowLock() throws Exception {
        byte[] content = new byte[CHUNK_SIZE];
        Arrays.fill(content, (byte) 11);
        UUID uploadId = initialize("concurrent.bin", content.length, sha256(content));
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<UploadProgressResponse>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> uploadService.uploadChunk(uploadId, 1, sha256(content), content));
            }
            var results = executor.invokeAll(tasks);
            for (var result : results) {
                assertThat(result.get().uploadedBytes()).isEqualTo(content.length);
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(chunkRepository.count()).isEqualTo(1);
        assertThat(uploadRepository.findById(uploadId).orElseThrow().getUploadedBytes()).isEqualTo(content.length);
    }

    @Test
    void uploadsOutOfOrderResumesIdempotentlyVerifiesAndDownloads() throws Exception {
        byte[] content = new byte[CHUNK_SIZE * 2 + 123];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i * 31);
        }
        String digest = sha256(content);
        UUID uploadId = initialize("candidate-model.bin", content.length, digest);

        upload(uploadId, 2, Arrays.copyOfRange(content, CHUNK_SIZE, CHUNK_SIZE * 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPartNumber").value(1))
                .andExpect(jsonPath("$.receivedParts.length()").value(1));

        upload(uploadId, 1, Arrays.copyOfRange(content, 0, CHUNK_SIZE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextPartNumber").value(3))
                .andExpect(jsonPath("$.uploadedBytes").value(CHUNK_SIZE * 2));

        upload(uploadId, 2, Arrays.copyOfRange(content, CHUNK_SIZE, CHUNK_SIZE * 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadedBytes").value(CHUNK_SIZE * 2));

        byte[] conflicting = Arrays.copyOfRange(content, CHUNK_SIZE, CHUNK_SIZE * 2);
        conflicting[0]++;
        upload(uploadId, 2, conflicting)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHUNK_CONFLICT"));

        upload(uploadId, 3, Arrays.copyOfRange(content, CHUNK_SIZE * 2, content.length))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyToComplete").value(true))
                .andExpect(jsonPath("$.nextPartNumber").doesNotExist());

        mockMvc.perform(post("/api/v1/uploads/{id}/complete", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualSha256").value(digest))
                .andExpect(jsonPath("$.downloadUri").value("/api/v1/uploads/" + uploadId + "/content"));

        MvcResult downloaded = mockMvc.perform(get("/api/v1/uploads/{id}/content", uploadId))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-SHA256", digest))
                .andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(content);

        mockMvc.perform(post("/api/v1/uploads/{id}/complete", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void hashFailurePreservesChunksAndBlocksDownload() throws Exception {
        byte[] content = new byte[CHUNK_SIZE];
        Arrays.fill(content, (byte) 7);
        UUID uploadId = initialize("wrong-digest.bin", content.length,
                "0000000000000000000000000000000000000000000000000000000000000000");
        upload(uploadId, 1, content).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/uploads/{id}/complete", uploadId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FILE_HASH_MISMATCH"));

        mockMvc.perform(get("/api/v1/uploads/{id}", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADING"))
                .andExpect(jsonPath("$.receivedParts[0]").value(1))
                .andExpect(jsonPath("$.readyToComplete").value(true));

        mockMvc.perform(get("/api/v1/uploads/{id}/content", uploadId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_NOT_COMPLETED"));
    }

    @Test
    void rejectsIncompleteUploadAndInvalidChunkIntegrity() throws Exception {
        byte[] content = new byte[CHUNK_SIZE * 2];
        UUID uploadId = initialize("incomplete.bin", content.length, sha256(content));
        upload(uploadId, 1, Arrays.copyOfRange(content, 0, CHUNK_SIZE)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/uploads/{id}/complete", uploadId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UPLOAD_INCOMPLETE"));

        mockMvc.perform(put("/api/v1/uploads/{id}/chunks/2", uploadId)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Chunk-SHA256", "0".repeat(64))
                        .content(Arrays.copyOfRange(content, CHUNK_SIZE, content.length)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CHUNK_HASH_MISMATCH"));

        mockMvc.perform(put("/api/v1/uploads/{id}/chunks/2", uploadId)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("X-Chunk-SHA256", sha256(content))
                        .content(new byte[10]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHUNK_SIZE_MISMATCH"));
    }

    @Test
    void rejectsPathTraversalInFileName() throws Exception {
        String body = """
                {
                  "fileName": "../../secret.txt",
                  "totalSize": 65536,
                  "chunkSize": 65536,
                  "sha256": "%s"
                }
                """.formatted("a".repeat(64));
        mockMvc.perform(post("/api/v1/uploads")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_NAME"));
    }

    private UUID initialize(String fileName, long size, String digest) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("fileName", fileName)
                .put("totalSize", size)
                .put("chunkSize", CHUNK_SIZE)
                .put("sha256", digest)
                .toString();
        MvcResult result = mockMvc.perform(post("/api/v1/uploads")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("UPLOADING"))
                .andReturn();
        return UUID.fromString(readJson(result).get("uploadId").asText());
    }

    private org.springframework.test.web.servlet.ResultActions upload(UUID uploadId, int part, byte[] content)
            throws Exception {
        return mockMvc.perform(put("/api/v1/uploads/{id}/chunks/{part}", uploadId, part)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Chunk-SHA256", sha256(content))
                .content(content));
    }
}
