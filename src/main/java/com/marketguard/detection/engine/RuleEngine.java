package com.marketguard.detection.engine;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.rule.DetectionRule;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.Duration;

/**
 * 등록된 모든 탐지 룰을 한 종목 컨텍스트에 대해 평가한다.
 * 룰 목록은 스프링이 DetectionRule 빈을 모두 주입해 채운다.
 */
public class RuleEngine {

    private static final System.Logger LOGGER = System.getLogger(RuleEngine.class.getName());

    private final List<DetectionRule> rules;
    private final RuleEvaluationObserver observer;

    public RuleEngine(List<DetectionRule> rules) {
        this(rules, (ruleType, outcome, duration) -> { });
    }

    public RuleEngine(List<DetectionRule> rules, RuleEvaluationObserver observer) {
        this.rules = List.copyOf(rules);
        this.observer = java.util.Objects.requireNonNull(observer, "observer must not be null");
        Set<com.marketguard.detection.model.RuleType> distinctTypes = this.rules.stream()
                .map(DetectionRule::type)
                .collect(Collectors.toSet());
        if (distinctTypes.size() != this.rules.size()) {
            throw new IllegalArgumentException("Detection rule types must be unique");
        }
        LOGGER.log(System.Logger.Level.INFO, "Loaded {0} detection rules: {1}", rules.size(),
                rules.stream().map(rule -> rule.type().name()).toList());
    }

    public List<Anomaly> evaluate(DetectionContext context) {
        return rules.stream()
                .map(rule -> evaluateSafely(rule, context))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<Anomaly> evaluateSafely(DetectionRule rule, DetectionContext context) {
        long startedAt = System.nanoTime();
        try {
            Optional<Anomaly> result = rule.evaluate(context);
            observer.observed(rule.type(), result.isPresent()
                    ? RuleEvaluationOutcome.SIGNAL : RuleEvaluationOutcome.NO_SIGNAL,
                    Duration.ofNanos(System.nanoTime() - startedAt));
            return result;
        } catch (RuntimeException exception) {
            observer.observed(rule.type(), RuleEvaluationOutcome.ERROR,
                    Duration.ofNanos(System.nanoTime() - startedAt));
            LOGGER.log(System.Logger.Level.WARNING, "Detection rule {0} failed for symbol {1} ({2})",
                    rule.type(), context.current().stockCode(), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
