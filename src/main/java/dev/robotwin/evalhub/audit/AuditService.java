package dev.robotwin.evalhub.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.robotwin.evalhub.common.ApiException;
import dev.robotwin.evalhub.common.RequestIdFilter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String entityType, UUID entityId, String action, Map<String, ?> detail) {
        try {
            String json = detail == null ? null : objectMapper.writeValueAsString(detail);
            repository.save(new AuditLog(entityType, entityId, action, currentActor(),
                    MDC.get(RequestIdFilter.REQUEST_ID), json));
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_SERIALIZATION_FAILED",
                    "Could not serialize audit detail", exception);
        }
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return "system";
        }
        return authentication.getName();
    }

    @Transactional(readOnly = true)
    public List<AuditLogResponse> list(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(AuditLogResponse::from)
                .toList();
    }
}
