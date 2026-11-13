package dev.robotwin.evalhub.model;

import java.time.Instant;
import java.util.UUID;

public record ModelVersionResponse(
        UUID id,
        String name,
        String version,
        String baseModel,
        String artifactUri,
        String artifactSha256,
        Instant createdAt) {

    public static ModelVersionResponse from(ModelVersion model) {
        return new ModelVersionResponse(model.getId(), model.getName(), model.getVersion(), model.getBaseModel(),
                model.getArtifactUri(), model.getArtifactSha256(), model.getCreatedAt());
    }
}
