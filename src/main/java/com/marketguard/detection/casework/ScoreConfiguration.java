package com.marketguard.detection.casework;

import com.marketguard.detection.model.RuleType;
import java.util.Map;

public record ScoreConfiguration(
        Map<RuleType, Integer> ruleWeights,
        int simultaneousSignalBonus,
        int maxScore,
        int mediumThreshold,
        int highThreshold
) {
    public ScoreConfiguration {
        ruleWeights = ruleWeights == null ? Map.of() : Map.copyOf(ruleWeights);
        if (ruleWeights.values().stream().anyMatch(value -> value == null || value < 0 || value > 100)) {
            throw new IllegalArgumentException("rule weights must be between 0 and 100");
        }
        if (simultaneousSignalBonus < 0 || simultaneousSignalBonus > 100) {
            throw new IllegalArgumentException("simultaneousSignalBonus must be between 0 and 100");
        }
        if (maxScore < 1 || maxScore > 100) {
            throw new IllegalArgumentException("maxScore must be between 1 and 100");
        }
        if (mediumThreshold < 0 || highThreshold <= mediumThreshold || highThreshold > maxScore) {
            throw new IllegalArgumentException("attention thresholds must increase within maxScore");
        }
    }
}
