package dev.robotwin.evalhub.model;

import dev.robotwin.evalhub.audit.AuditService;
import dev.robotwin.evalhub.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelVersionService {

    private final ModelVersionRepository repository;
    private final AuditService auditService;

    public ModelVersionService(ModelVersionRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional
    public ModelVersionResponse register(RegisterModelRequest request) {
        if (repository.existsByNameAndVersion(request.name(), request.version())) {
            throw ApiException.conflict("MODEL_VERSION_EXISTS", "Model name and version already exist");
        }
        ModelVersion model = new ModelVersion(request.name(), request.version(), request.baseModel(),
                request.artifactUri(), request.artifactSha256().toLowerCase());
        repository.save(model);
        auditService.record("MODEL", model.getId(), "MODEL_REGISTERED",
                Map.of("name", model.getName(), "version", model.getVersion()));
        return ModelVersionResponse.from(model);
    }

    @Transactional(readOnly = true)
    public ModelVersion get(UUID id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Model version", id));
    }

    @Transactional(readOnly = true)
    public ModelVersionResponse detail(UUID id) {
        return ModelVersionResponse.from(get(id));
    }

    @Transactional(readOnly = true)
    public List<ModelVersionResponse> list() {
        return repository.findAll().stream().map(ModelVersionResponse::from).toList();
    }
}
