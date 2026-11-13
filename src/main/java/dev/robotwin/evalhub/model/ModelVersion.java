package dev.robotwin.evalhub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "model_version")
public class ModelVersion {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String version;

    private String baseModel;

    @Column(nullable = false)
    private String artifactUri;

    @Column(nullable = false)
    private String artifactSha256;

    @Column(nullable = false)
    private Instant createdAt;

    protected ModelVersion() {
    }

    public ModelVersion(String name, String version, String baseModel, String artifactUri, String artifactSha256) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.version = version;
        this.baseModel = baseModel;
        this.artifactUri = artifactUri;
        this.artifactSha256 = artifactSha256;
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

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getBaseModel() {
        return baseModel;
    }

    public String getArtifactUri() {
        return artifactUri;
    }

    public String getArtifactSha256() {
        return artifactSha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
