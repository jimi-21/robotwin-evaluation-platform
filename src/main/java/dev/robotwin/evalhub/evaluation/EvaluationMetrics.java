package dev.robotwin.evalhub.evaluation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public record EvaluationMetrics(
        int successCount,
        int failureCount,
        int timeoutCount,
        BigDecimal successRate,
        BigDecimal meanSuccessSteps,
        BigDecimal meanJitter) {

    public static EvaluationMetrics from(List<EvaluationEpisode> episodes) {
        int success = (int) episodes.stream().filter(e -> e.getResult() == EpisodeResult.SUCCESS).count();
        int failure = (int) episodes.stream().filter(e -> e.getResult() == EpisodeResult.FAILURE).count();
        int timeout = (int) episodes.stream().filter(e -> e.getResult() == EpisodeResult.TIMEOUT).count();
        BigDecimal successRate = episodes.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(success)
                .divide(BigDecimal.valueOf(episodes.size()), 6, RoundingMode.HALF_UP);
        List<EvaluationEpisode> successful = episodes.stream()
                .filter(e -> e.getResult() == EpisodeResult.SUCCESS).toList();
        BigDecimal meanSteps = successful.isEmpty() ? null : BigDecimal.valueOf(
                        successful.stream().mapToLong(EvaluationEpisode::getSteps).sum())
                .divide(BigDecimal.valueOf(successful.size()), 1, RoundingMode.HALF_UP);
        List<BigDecimal> jitters = episodes.stream().map(EvaluationEpisode::getJitter)
                .filter(java.util.Objects::nonNull).toList();
        BigDecimal meanJitter = jitters.isEmpty() ? null : jitters.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(jitters.size()), 6, RoundingMode.HALF_UP);
        return new EvaluationMetrics(success, failure, timeout, successRate, meanSteps, meanJitter);
    }
}
