package dev.robotwin.evalhub.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotNull @Positive Long totalSize,
        @NotNull @Positive Integer chunkSize,
        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{64}", message = "must be a SHA256 hex digest") String sha256) {
}
