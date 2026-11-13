package dev.robotwin.evalhub.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import dev.robotwin.evalhub.model.ModelVersionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvaluationResponse(
        UUID id,
        ModelVersionResponse model,
        String name,
        EvaluationStatus status,
        JsonNode configSnapshot,
        JsonNode scoreSnapshot,
        int attempt,
        int maxAttempts,
        Integer successCount,
        Integer failureCount,
        Integer timeoutCount,
        BigDecimal successRate,
        BigDecimal meanSuccessSteps,
        BigDecimal meanJitter,
        BigDecimal totalScore,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        long version) {
}
