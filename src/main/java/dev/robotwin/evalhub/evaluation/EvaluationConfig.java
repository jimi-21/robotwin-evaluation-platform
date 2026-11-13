package dev.robotwin.evalhub.evaluation;

import java.util.List;
import java.util.Map;

public record EvaluationConfig(
        List<Integer> sceneSeeds,
        int inferenceSeed,
        int maxSteps,
        int numInferenceSteps,
        Map<String, Object> parameters) {
}
