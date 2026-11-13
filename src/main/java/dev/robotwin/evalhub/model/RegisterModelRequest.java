package dev.robotwin.evalhub.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterModelRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 100) String version,
        @Size(max = 200) String baseModel,
        @NotBlank @Size(max = 1000) String artifactUri,
        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}", message = "must be a SHA256 hex digest") String artifactSha256) {
}
