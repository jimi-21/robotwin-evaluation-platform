package dev.robotwin.evalhub.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{entityType}/{entityId}")
    public List<AuditLogResponse> list(@PathVariable String entityType, @PathVariable UUID entityId) {
        return auditService.list(entityType.toUpperCase(), entityId);
    }
}
