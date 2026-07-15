package com.marketguard.detection.engine;

import com.marketguard.detection.model.RuleType;
import java.time.Duration;

@FunctionalInterface
public interface RuleEvaluationObserver {
    void observed(RuleType ruleType, RuleEvaluationOutcome outcome, Duration duration);
}
