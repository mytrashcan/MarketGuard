package com.marketguard.detection.engine;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.rule.DetectionRule;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 등록된 모든 탐지 룰을 한 종목 컨텍스트에 대해 평가한다.
 * 룰 목록은 스프링이 DetectionRule 빈을 모두 주입해 채운다.
 */
public class RuleEngine {

    private static final System.Logger LOGGER = System.getLogger(RuleEngine.class.getName());

    private final List<DetectionRule> rules;

    public RuleEngine(List<DetectionRule> rules) {
        this.rules = List.copyOf(rules);
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
        try {
            return rule.evaluate(context);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "Detection rule {0} failed for symbol {1} ({2})",
                    rule.type(), context.current().stockCode(), exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
