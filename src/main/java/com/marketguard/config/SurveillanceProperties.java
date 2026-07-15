package com.marketguard.config;

import com.marketguard.detection.casework.ScoreConfiguration;
import com.marketguard.detection.model.RuleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "surveillance")
public record SurveillanceProperties(
        @NotNull Duration groupingWindow,
        @NotNull Duration inactivityTimeout,
        @NotNull Duration reactivationWindow,
        Map<RuleType, Integer> ruleWeights,
        @Min(0) @Max(100) int simultaneousSignalBonus,
        @Min(1) @Max(100) int maxScore,
        @Min(0) @Max(99) int mediumThreshold,
        @Min(1) @Max(100) int highThreshold
) {
    public SurveillanceProperties {
        ruleWeights = ruleWeights == null ? Map.of() : Map.copyOf(ruleWeights);
        if (groupingWindow != null && groupingWindow.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("groupingWindow must be at least one minute");
        }
        if (inactivityTimeout != null && groupingWindow != null
                && inactivityTimeout.compareTo(groupingWindow) < 0) {
            throw new IllegalArgumentException("inactivityTimeout must not be shorter than groupingWindow");
        }
        if (reactivationWindow != null && groupingWindow != null
                && reactivationWindow.compareTo(groupingWindow) < 0) {
            throw new IllegalArgumentException("reactivationWindow must not be shorter than groupingWindow");
        }
    }

    public ScoreConfiguration scoreConfiguration() {
        return new ScoreConfiguration(ruleWeights, simultaneousSignalBonus, maxScore,
                mediumThreshold, highThreshold);
    }
}
