package dev.robotwin.evalhub.upload;

import dev.robotwin.evalhub.audit.AuditService;
import dev.robotwin.evalhub.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final int MIN_CHUNK_SIZE = 64 * 1024;
    private static final int MAX_CHUNK_SIZE = 16 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 1024L * 1024 * 1024 * 1024;
    private static final int MAX_CHUNKS = 100_000;

    private final UploadSessionRepository sessionRepository;
    private final UploadChunkRepository chunkRepository;
    private final FileStorage storage;
    private final AuditService auditService;

    public UploadService(UploadSessionRepository sessionRepository, UploadChunkRepository chunkRepository,
            FileStorage storage, AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.chunkRepository = chunkRepository;
        this.storage = storage;
        this.auditService = auditService;
    }

    @Transactional
    public UploadProgressResponse initialize(CreateUploadRequest request) {
        validateFileName(request.fileName());
        if (request.chunkSize() < MIN_CHUNK_SIZE || request.chunkSize() > MAX_CHUNK_SIZE) {
            throw ApiException.badRequest("INVALID_CHUNK_SIZE", "chunkSize must be between 65536 and 16777216 bytes");
        }
        if (request.totalSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("FILE_TOO_LARGE", "totalSize exceeds the 1 TiB application limit");
        }
        long totalChunks = (request.totalSize() - 1) / request.chunkSize() + 1;
        if (totalChunks > MAX_CHUNKS) {
            throw ApiException.badRequest("TOO_MANY_CHUNKS", "Upload would exceed the maximum chunk count");
        }
        UploadSession session = new UploadSession(request.fileName(), request.totalSize(), request.chunkSize(),
                (int) totalChunks, request.sha256().toLowerCase(Locale.ROOT));
        sessionRepository.save(session);
        auditService.record("UPLOAD", session.getId(), "UPLOAD_INITIALIZED", Map.of(
                "fileName", session.getFileName(), "totalSize", session.getTotalSize(), "totalChunks", totalChunks));
        return progressResponse(session, List.of());
    }

    @Transactional
    public UploadProgressResponse uploadChunk(UUID uploadId, int partNumber, String suppliedSha256, byte[] content) {
        UploadSession session = getLockedSession(uploadId);
        if (session.getStatus() != UploadStatus.UPLOADING) {
            throw ApiException.conflict("UPLOAD_ALREADY_COMPLETED", "No chunks are accepted after completion");
        }
        long expectedSize = expectedPartSize(session, partNumber);
        if (content.length != expectedSize) {
            throw ApiException.badRequest("CHUNK_SIZE_MISMATCH",
                    "Expected " + expectedSize + " bytes for part " + partNumber + " but received " + content.length);
        }
        String actualSha256 = storage.hashBytes(content);
        if (!actualSha256.equalsIgnoreCase(suppliedSha256)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CHUNK_HASH_MISMATCH",
                    "X-Chunk-SHA256 does not match the chunk content");
        }
        var existing = chunkRepository.findByUploadSessionIdAndPartNumber(uploadId, partNumber);
        if (existing.isPresent()) {
            UploadChunk chunk = existing.get();
            if (chunk.getSha256().equals(actualSha256) && chunk.getChunkSize() == content.length) {
                return progressResponse(session, chunkRepository.findByUploadSessionIdOrderByPartNumberAsc(uploadId));
            }
            throw ApiException.conflict("CHUNK_CONFLICT", "A different chunk already exists for this part number");
        }
        Path path = storage.saveChunk(uploadId, partNumber, content);
        try {
            chunkRepository.save(new UploadChunk(session, partNumber, content.length, actualSha256, path.toString()));
            session.addUploadedBytes(content.length);
        } catch (RuntimeException exception) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
        return progressResponse(session, chunkRepository.findByUploadSessionIdOrderByPartNumberAsc(uploadId));
    }

    @Transactional(readOnly = true)
    public UploadProgressResponse progress(UUID uploadId) {
        UploadSession session = getSession(uploadId);
        return progressResponse(session, chunkRepository.findByUploadSessionIdOrderByPartNumberAsc(uploadId));
    }

    @Transactional
    public UploadProgressResponse complete(UUID uploadId) {
        UploadSession session = getLockedSession(uploadId);
        List<UploadChunk> chunks = chunkRepository.findByUploadSessionIdOrderByPartNumberAsc(uploadId);
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return progressResponse(session, chunks);
        }
        if (chunks.size() != session.getTotalChunks()) {
            throw ApiException.conflict("UPLOAD_INCOMPLETE",
                    "Expected " + session.getTotalChunks() + " chunks but received " + chunks.size());
        }
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).getPartNumber() != i + 1) {
                throw ApiException.conflict("UPLOAD_INCOMPLETE", "Upload has a gap at part " + (i + 1));
            }
        }
        FileStorage.StoredFile assembled = storage.assemble(uploadId, session.getFileName(), chunks,
                session.getTotalSize());
        if (!assembled.sha256().equals(session.getExpectedSha256())) {
            storage.deleteFile(assembled.path());
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FILE_HASH_MISMATCH",
                    "Assembled file SHA256 differs from the declared digest; chunks were preserved");
        }
        session.complete(assembled.sha256(), assembled.path().toString());
        auditService.record("UPLOAD", uploadId, "UPLOAD_COMPLETED", Map.of(
                "totalSize", assembled.size(), "sha256", assembled.sha256()));
        UploadProgressResponse response = progressResponse(session, chunks);
        chunks.forEach(this::deleteChunkQuietly);
        return response;
    }

    @Transactional(readOnly = true)
    public DownloadFile download(UUID uploadId) {
        UploadSession session = getSession(uploadId);
        if (session.getStatus() != UploadStatus.COMPLETED) {
            throw ApiException.conflict("UPLOAD_NOT_COMPLETED", "File is not available before SHA256 verification");
        }
        Path path = Path.of(session.getStoragePath());
        if (!Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_FILE_MISSING", "Completed file is missing");
        }
        return new DownloadFile(path, session.getFileName(), session.getTotalSize(), session.getActualSha256());
    }

    private UploadProgressResponse progressResponse(UploadSession session, List<UploadChunk> chunks) {
        List<Integer> parts = chunks.stream().map(UploadChunk::getPartNumber).toList();
        var receivedParts = new HashSet<>(parts);
        Integer next = null;
        if (session.getStatus() == UploadStatus.UPLOADING) {
            for (int part = 1; part <= session.getTotalChunks(); part++) {
                if (!receivedParts.contains(part)) {
                    next = part;
                    break;
                }
            }
        }
        String downloadUri = session.getStatus() == UploadStatus.COMPLETED
                ? "/api/v1/uploads/" + session.getId() + "/content" : null;
        return new UploadProgressResponse(session.getId(), session.getFileName(), session.getTotalSize(),
                session.getChunkSize(), session.getTotalChunks(), session.getExpectedSha256(),
                session.getActualSha256(), session.getUploadedBytes(), session.getStatus(), parts, next,
                session.getStatus() == UploadStatus.UPLOADING && chunks.size() == session.getTotalChunks(),
                downloadUri, session.getCreatedAt(), session.getCompletedAt());
    }

    private long expectedPartSize(UploadSession session, int partNumber) {
        if (partNumber < 1 || partNumber > session.getTotalChunks()) {
            throw ApiException.badRequest("INVALID_PART_NUMBER", "partNumber is outside the declared upload plan");
        }
        if (partNumber < session.getTotalChunks()) {
            return session.getChunkSize();
        }
        return session.getTotalSize() - (long) session.getChunkSize() * (session.getTotalChunks() - 1);
    }

    private UploadSession getSession(UUID id) {
        return sessionRepository.findById(id).orElseThrow(() -> ApiException.notFound("Upload", id));
    }

    private UploadSession getLockedSession(UUID id) {
        return sessionRepository.findLockedById(id).orElseThrow(() -> ApiException.notFound("Upload", id));
    }

    private void validateFileName(String fileName) {
        if (fileName.equals(".") || fileName.equals("..") || fileName.contains("/") || fileName.contains("\\")
                || fileName.chars().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_FILE_NAME", "fileName must be a plain file name without paths");
        }
    }

    private void deleteChunkQuietly(UploadChunk chunk) {
        try {
            storage.deleteChunk(chunk);
        } catch (RuntimeException exception) {
            log.warn("Could not delete chunk {} of upload {}", chunk.getPartNumber(),
                    chunk.getUploadSession().getId(), exception);
        }
    }

    public record DownloadFile(Path path, String fileName, long size, String sha256) {
    }
}
