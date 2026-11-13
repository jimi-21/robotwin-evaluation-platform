package dev.robotwin.evalhub.evaluation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CreateEvaluationRequest(
        @NotNull UUID modelVersionId,
        @NotBlank @Size(max = 120) String name,
        @NotEmpty List<@NotNull @Min(0) Integer> sceneSeeds,
        @Min(0) Integer inferenceSeed,
        @Min(1) @Max(100000) Integer maxSteps,
        @Min(1) @Max(1000) Integer numInferenceSteps,
        @Min(1) @Max(3) Integer maxAttempts,
        Map<@NotBlank String, Object> parameters) {

    public CreateEvaluationRequest {
        inferenceSeed = inferenceSeed == null ? 42 : inferenceSeed;
        maxSteps = maxSteps == null ? 400 : maxSteps;
        numInferenceSteps = numInferenceSteps == null ? 10 : numInferenceSteps;
        maxAttempts = maxAttempts == null ? 1 : maxAttempts;
        sceneSeeds = sceneSeeds == null ? null : List.copyOf(sceneSeeds);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
