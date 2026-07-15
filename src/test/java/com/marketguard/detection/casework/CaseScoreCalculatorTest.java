package com.marketguard.detection.casework;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CaseScoreCalculatorTest {

    private final CaseScoreCalculator calculator = new CaseScoreCalculator(new ScoreConfiguration(
            Map.of(RuleType.PRICE_SPIKE, 25, RuleType.VOLUME_SURGE, 25,
                    RuleType.ORDERBOOK_IMBALANCE, 20), 10, 100, 30, 60));

    @Test
    void combinesDistinctRulesAndAddsTheConfiguredBonus() {
        CompositeScore result = calculator.calculate(List.of(
                anomaly(RuleType.PRICE_SPIKE, "5"),
                anomaly(RuleType.VOLUME_SURGE, "6"),
                anomaly(RuleType.PRICE_SPIKE, "7")));

        assertThat(result.value()).isEqualTo(60);
        assertThat(result.attentionLevel()).isEqualTo(AttentionLevel.HIGH);
        assertThat(result.simultaneousSignalBonus()).isEqualTo(10);
        assertThat(result.contributions()).extracting(ScoreContribution::ruleType)
                .containsExactly(RuleType.PRICE_SPIKE, RuleType.VOLUME_SURGE);
        assertThat(result.explanation()).contains("동시 발생 보너스 10점");
    }

    @Test
    void clampsTheScoreToTheConfiguredMaximum() {
        CaseScoreCalculator capped = new CaseScoreCalculator(new ScoreConfiguration(
                Map.of(RuleType.PRICE_SPIKE, 80, RuleType.VOLUME_SURGE, 80), 40, 90, 30, 60));

        assertThat(capped.calculate(List.of(
                anomaly(RuleType.PRICE_SPIKE, "5"), anomaly(RuleType.VOLUME_SURGE, "6"))).value())
                .isEqualTo(90);
    }

    private static Anomaly anomaly(RuleType type, String observed) {
        Instant time = Instant.parse("2026-07-15T01:00:00Z");
        AnomalyEvidence evidence = AnomalyEvidence.builder(type.description(), "summary", "explanation")
                .values(new BigDecimal(observed), BigDecimal.ONE, new BigDecimal("3"),
                        new BigDecimal("2"), "배")
                .recommendedChecks(List.of("확인"))
                .marketObservedAt(time)
                .build();
        return Anomaly.explained("005930", type, Severity.WARNING, "message", time, evidence);
    }
}
