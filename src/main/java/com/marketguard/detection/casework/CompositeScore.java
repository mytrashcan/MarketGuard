package com.marketguard.detection.casework;

import java.util.List;

public record CompositeScore(
        int value,
        AttentionLevel attentionLevel,
        int simultaneousSignalBonus,
        List<ScoreContribution> contributions,
        String explanation
) {
    public CompositeScore {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("score must be between 0 and 100");
        }
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
    }
}
