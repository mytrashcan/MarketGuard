package com.marketguard.detection.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.rule.DetectionRule;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final DetectionContext context = new DetectionContext(
            new PriceSnapshot("005930", new BigDecimal("70000"), Instant.parse("2026-07-15T00:00:00Z")),
            List.of());

    @Test
    void combinesAnomaliesFromAllMatchingRulesInRegistrationOrder() {
        DetectionRule first = matchingRule(RuleType.PRICE_SPIKE);
        DetectionRule second = matchingRule(RuleType.VOLUME_SURGE);

        List<Anomaly> result = new RuleEngine(List.of(first, second)).evaluate(context);

        assertThat(result).extracting(Anomaly::ruleType)
                .containsExactly(RuleType.PRICE_SPIKE, RuleType.VOLUME_SURGE);
    }

    @Test
    void documentsThatOneRuleFailureCurrentlyStopsFollowingRules() {
        AtomicBoolean followingRuleCalled = new AtomicBoolean();
        DetectionRule broken = new TestRule(RuleType.PRICE_SPIKE, ignored -> {
            throw new IllegalStateException("broken rule");
        });
        DetectionRule following = new TestRule(RuleType.VOLUME_SURGE, ignored -> {
            followingRuleCalled.set(true);
            return Optional.empty();
        });

        RuleEngine engine = new RuleEngine(List.of(broken, following));

        assertThatThrownBy(() -> engine.evaluate(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("broken rule");
        assertThat(followingRuleCalled).isFalse();
    }

    private DetectionRule matchingRule(RuleType type) {
        return new TestRule(type, ignored -> Optional.of(new Anomaly(
                "005930", type, Severity.WARNING, "test", Instant.parse("2026-07-15T00:00:00Z"))));
    }

    @FunctionalInterface
    private interface Evaluation {
        Optional<Anomaly> apply(DetectionContext context);
    }

    private record TestRule(RuleType type, Evaluation evaluation) implements DetectionRule {

        @Override
        public Optional<Anomaly> evaluate(DetectionContext context) {
            return evaluation.apply(context);
        }
    }
}
