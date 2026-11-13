package dev.robotwin.evalhub.upload;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadProgressResponse(
        UUID uploadId,
        String fileName,
        long totalSize,
        int chunkSize,
        int totalChunks,
        String expectedSha256,
        String actualSha256,
        long uploadedBytes,
        UploadStatus status,
        List<Integer> receivedParts,
        Integer nextPartNumber,
        boolean readyToComplete,
        String downloadUri,
        Instant createdAt,
        Instant completedAt) {
}
