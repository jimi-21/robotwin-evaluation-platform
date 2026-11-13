package dev.robotwin.evalhub.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        String entityType,
        UUID entityId,
        String action,
        String actor,
        String requestId,
        String detailJson,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEntityType(), log.getEntityId(), log.getAction(),
                log.getActor(), log.getRequestId(), log.getDetailJson(), log.getCreatedAt());
    }
}
