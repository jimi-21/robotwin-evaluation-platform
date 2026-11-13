package dev.robotwin.evalhub.evaluation;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreAuditImportRequest(
        @JsonProperty("episode_records") @NotEmpty @Valid List<EpisodeImportRequest> episodeRecords,
        @NotNull @Valid ScoreRequest score) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EpisodeImportRequest(
            @NotNull @Min(0) Integer episode,
            @NotNull @Min(0) Integer seed,
            @JsonAlias("arm_tag") String armTag,
            @NotNull Boolean success,
            @NotNull @Min(1) Integer steps,
            @DecimalMin("0.0") BigDecimal jitter,
            String instruction,
            String video) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ScoreRequest(
            @NotNull BigDecimal total,
            @JsonProperty("success_rate") BigDecimal successRate,
            @JsonProperty("success_score") BigDecimal successScore,
            @JsonProperty("efficiency_score") BigDecimal efficiencyScore,
            @JsonProperty("stability_score") BigDecimal stabilityScore,
            @JsonProperty("mean_success_steps") BigDecimal meanSuccessSteps,
            @JsonProperty("mean_jitter") BigDecimal meanJitter,
            @JsonProperty("segment_stats") List<Map<String, Object>> segmentStats) {
    }
}
