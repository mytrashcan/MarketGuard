package com.marketguard.detection.casework;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 서로 다른 규칙만 한 번씩 반영하는 설명 가능한 복합 점수 계산기. */
public final class CaseScoreCalculator {

    private final ScoreConfiguration configuration;

    public CaseScoreCalculator(ScoreConfiguration configuration) {
        this.configuration = configuration;
    }

    public CompositeScore calculate(Collection<Anomaly> signals) {
        Map<RuleType, Anomaly> latestByRule = new EnumMap<>(RuleType.class);
        if (signals != null) {
            signals.stream()
                    .sorted(Comparator.comparing(Anomaly::detectedAt))
                    .forEach(signal -> latestByRule.put(signal.ruleType(), signal));
        }
        List<ScoreContribution> contributions = latestByRule.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> contribution(entry.getKey(), entry.getValue()))
                .toList();
        int subtotal = contributions.stream().mapToInt(ScoreContribution::points).sum();
        int bonus = contributions.size() >= 2 ? configuration.simultaneousSignalBonus() : 0;
        int score = Math.min(configuration.maxScore(), subtotal + bonus);
        AttentionLevel level = score >= configuration.highThreshold() ? AttentionLevel.HIGH
                : score >= configuration.mediumThreshold() ? AttentionLevel.MEDIUM : AttentionLevel.LOW;
        String explanation = "서로 다른 규칙 %d개 기여점수 %d점%s, 최대 %d점으로 제한"
                .formatted(contributions.size(), subtotal,
                        bonus > 0 ? " + 동시 발생 보너스 " + bonus + "점" : "",
                        configuration.maxScore());
        return new CompositeScore(score, level, bonus, contributions, explanation);
    }

    private ScoreContribution contribution(RuleType ruleType, Anomaly signal) {
        int points = configuration.ruleWeights().getOrDefault(ruleType, 0);
        return new ScoreContribution(ruleType, points, signal.evidence().summary(),
                signal.evidence().observedValue(), signal.evidence().baselineValue(),
                signal.evidence().thresholdValue(), signal.evidence().valueUnit());
    }
}
