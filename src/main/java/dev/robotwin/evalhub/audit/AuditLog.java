package dev.robotwin.evalhub.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID id;

    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private UUID entityId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    private String requestId;

    @Column(columnDefinition = "TEXT")
    private String detailJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    public AuditLog(String entityType, UUID entityId, String action, String actor, String requestId, String detailJson) {
        this.id = UUID.randomUUID();
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.actor = actor;
        this.requestId = requestId;
        this.detailJson = detailJson;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getActor() {
        return actor;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
