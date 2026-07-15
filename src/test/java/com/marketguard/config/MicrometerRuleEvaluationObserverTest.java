package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.engine.RuleEvaluationOutcome;
import com.marketguard.detection.model.RuleType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class MicrometerRuleEvaluationObserverTest {
    @Test
    void recordsBoundedRuleAndOutcomeTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerRuleEvaluationObserver observer = new MicrometerRuleEvaluationObserver(registry);

        observer.observed(RuleType.PRICE_SPIKE, RuleEvaluationOutcome.SIGNAL, Duration.ofMillis(3));

        assertThat(registry.get("marketguard.rule.evaluations")
                .tags("rule", "PRICE_SPIKE", "outcome", "signal").counter().count()).isEqualTo(1);
        assertThat(registry.get("marketguard.rule.evaluation.duration")
                .tag("rule", "PRICE_SPIKE").timer().count()).isEqualTo(1);
    }
}
