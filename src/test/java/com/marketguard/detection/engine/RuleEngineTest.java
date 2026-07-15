package com.marketguard.detection.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.rule.DetectionRule;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RuleEngineTest {

    private final DetectionContext context = new DetectionContext(
            new MarketPrice("005930", new BigDecimal("70000"), Instant.parse("2026-07-15T00:00:00Z")),
            List.of(), Instant.parse("2026-07-15T00:00:01Z"));

    @Test
    void combinesAnomaliesFromAllMatchingRulesInRegistrationOrder() {
        DetectionRule first = matchingRule(RuleType.PRICE_SPIKE);
        DetectionRule second = matchingRule(RuleType.VOLUME_SURGE);

        List<Anomaly> result = new RuleEngine(List.of(first, second)).evaluate(context);

        assertThat(result).extracting(Anomaly::ruleType)
                .containsExactly(RuleType.PRICE_SPIKE, RuleType.VOLUME_SURGE);
    }

    @Test
    void isolatesOneRuleFailureAndContinuesWithFollowingRules() {
        AtomicBoolean followingRuleCalled = new AtomicBoolean();
        DetectionRule broken = new TestRule(RuleType.PRICE_SPIKE, ignored -> {
            throw new IllegalStateException("broken rule");
        });
        DetectionRule following = new TestRule(RuleType.VOLUME_SURGE, ignored -> {
            followingRuleCalled.set(true);
            return Optional.of(new Anomaly("005930", RuleType.VOLUME_SURGE, Severity.WARNING,
                    "healthy", Instant.parse("2026-07-15T00:00:01Z")));
        });

        RuleEngine engine = new RuleEngine(List.of(broken, following));

        assertThat(engine.evaluate(context)).singleElement()
                .extracting(Anomaly::ruleType)
                .isEqualTo(RuleType.VOLUME_SURGE);
        assertThat(followingRuleCalled).isTrue();
    }

    @Test
    void rejectsDuplicateRuleTypes() {
        assertThatThrownBy(() -> new RuleEngine(List.of(
                matchingRule(RuleType.PRICE_SPIKE), matchingRule(RuleType.PRICE_SPIKE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void reportsRuleOutcomeWithoutBreakingIsolation() {
        AtomicReference<RuleEvaluationOutcome> outcome = new AtomicReference<>();
        RuleEngine engine = new RuleEngine(List.of(matchingRule(RuleType.PRICE_SPIKE)),
                (ruleType, value, duration) -> outcome.set(value));

        engine.evaluate(context);

        assertThat(outcome).hasValue(RuleEvaluationOutcome.SIGNAL);
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
