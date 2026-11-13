package dev.robotwin.evalhub.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "upload_chunk")
public class UploadChunk {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_session_id", nullable = false)
    private UploadSession uploadSession;

    private int partNumber;
    private long chunkSize;
    private String sha256;
    private String storagePath;

    @Column(nullable = false)
    private Instant createdAt;

    protected UploadChunk() {
    }

    public UploadChunk(UploadSession uploadSession, int partNumber, long chunkSize, String sha256, String storagePath) {
        this.id = UUID.randomUUID();
        this.uploadSession = uploadSession;
        this.partNumber = partNumber;
        this.chunkSize = chunkSize;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UploadSession getUploadSession() {
        return uploadSession;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public long getChunkSize() {
        return chunkSize;
    }

    public String getSha256() {
        return sha256;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
