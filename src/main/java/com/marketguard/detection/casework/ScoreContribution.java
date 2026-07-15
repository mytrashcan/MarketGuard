package com.marketguard.detection.casework;

import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;

public record ScoreContribution(
        RuleType ruleType,
        int points,
        String summary,
        BigDecimal observedValue,
        BigDecimal baselineValue,
        BigDecimal thresholdValue,
        String unit
) {
}
