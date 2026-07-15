package com.marketguard.config;

import com.marketguard.detection.engine.RuleEvaluationObserver;
import com.marketguard.detection.engine.RuleEvaluationOutcome;
import com.marketguard.detection.model.RuleType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MicrometerRuleEvaluationObserver implements RuleEvaluationObserver {

    private final Map<RuleType, Timer> timers = new EnumMap<>(RuleType.class);
    private final Map<RuleType, Map<RuleEvaluationOutcome, Counter>> outcomes = new EnumMap<>(RuleType.class);

    public MicrometerRuleEvaluationObserver(MeterRegistry registry) {
        for (RuleType rule : RuleType.values()) {
            timers.put(rule, Timer.builder("marketguard.rule.evaluation.duration")
                    .description("Detection rule evaluation duration")
                    .tag("rule", rule.name()).register(registry));
            Map<RuleEvaluationOutcome, Counter> counters = new EnumMap<>(RuleEvaluationOutcome.class);
            for (RuleEvaluationOutcome outcome : RuleEvaluationOutcome.values()) {
                counters.put(outcome, Counter.builder("marketguard.rule.evaluations")
                        .description("Detection rule evaluations by outcome")
                        .tag("rule", rule.name()).tag("outcome", outcome.name().toLowerCase())
                        .register(registry));
            }
            outcomes.put(rule, counters);
        }
    }

    @Override
    public void observed(RuleType ruleType, RuleEvaluationOutcome outcome, Duration duration) {
        timers.get(ruleType).record(duration);
        outcomes.get(ruleType).get(outcome).increment();
    }
}
