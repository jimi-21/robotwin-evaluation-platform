package dev.robotwin.evalhub.evaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EvaluationEpisodeResponse(
        UUID id,
        int episodeIndex,
        int sceneSeed,
        EpisodeResult result,
        int steps,
        BigDecimal jitter,
        String instruction,
        String videoUri,
        String armTag,
        String failureReason,
        Instant createdAt) {

    public static EvaluationEpisodeResponse from(EvaluationEpisode episode) {
        return new EvaluationEpisodeResponse(episode.getId(), episode.getEpisodeIndex(), episode.getSceneSeed(),
                episode.getResult(), episode.getSteps(), episode.getJitter(), episode.getInstruction(),
                episode.getVideoUri(), episode.getArmTag(), episode.getFailureReason(), episode.getCreatedAt());
    }
}
