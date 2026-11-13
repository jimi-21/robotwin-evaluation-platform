package dev.robotwin.evalhub.evaluation;

import java.util.UUID;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EvaluationDispatcher {

    private final EvaluationExecutor executor;

    public EvaluationDispatcher(EvaluationExecutor executor) {
        this.executor = executor;
    }

    @Async("evaluationTaskExecutor")
    public void dispatch(UUID evaluationId) {
        executor.execute(evaluationId);
    }
}
