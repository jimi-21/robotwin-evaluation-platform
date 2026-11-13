package dev.robotwin.evalhub.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "upload_session")
public class UploadSession {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false)
    private String fileName;

    @Column(nullable = false)
    private long totalSize;

    @Column(nullable = false)
    private int chunkSize;

    @Column(nullable = false)
    private int totalChunks;

    @Column(nullable = false)
    private String expectedSha256;
    private String actualSha256;
    private long uploadedBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UploadStatus status;

    private String storagePath;

    @Column(nullable = false)
    private Instant createdAt;
    private Instant completedAt;

    @Version
    private long version;

    protected UploadSession() {
    }

    public UploadSession(String fileName, long totalSize, int chunkSize, int totalChunks, String expectedSha256) {
        this.id = UUID.randomUUID();
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.expectedSha256 = expectedSha256;
        this.status = UploadStatus.UPLOADING;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void addUploadedBytes(long bytes) {
        this.uploadedBytes += bytes;
    }

    public void complete(String actualSha256, String storagePath) {
        this.status = UploadStatus.COMPLETED;
        this.actualSha256 = actualSha256;
        this.storagePath = storagePath;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public String getExpectedSha256() {
        return expectedSha256;
    }

    public String getActualSha256() {
        return actualSha256;
    }

    public long getUploadedBytes() {
        return uploadedBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public long getVersion() {
        return version;
    }
}
