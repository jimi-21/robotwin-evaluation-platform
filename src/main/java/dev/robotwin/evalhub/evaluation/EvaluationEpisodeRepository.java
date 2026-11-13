package dev.robotwin.evalhub.evaluation;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationEpisodeRepository extends JpaRepository<EvaluationEpisode, UUID> {

    List<EvaluationEpisode> findByEvaluationRunIdOrderByEpisodeIndexAsc(UUID evaluationRunId);

    long deleteByEvaluationRunId(UUID evaluationRunId);
}
