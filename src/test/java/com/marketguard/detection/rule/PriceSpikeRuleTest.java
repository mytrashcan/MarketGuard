package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceSpikeRuleTest {

    private final PriceSpikeRule rule =
            new PriceSpikeRule(new BigDecimal("3.0"), 20);

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-15T00:00:00Z");

    private MarketPrice snapshot(String price) {
        return new MarketPrice("005930", new BigDecimal(price), EVALUATED_AT);
    }

    private List<MarketPrice> recentWithPrice(String price, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> snapshot(price))
                .toList();
    }

    @Test
    @DisplayName("현재가가 직전 평균 대비 임계치 이상 급변하면 탐지한다")
    void detectsSpike() {
        DetectionContext context =
                new DetectionContext(snapshot("10400"), recentWithPrice("10000", 5), EVALUATED_AT); // +4%

        Optional<Anomaly> result = rule.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleType()).isEqualTo(RuleType.PRICE_SPIKE);
    }

    @Test
    @DisplayName("임계치의 2배 이상이면 CRITICAL로 분류한다")
    void classifiesCritical() {
        DetectionContext context =
                new DetectionContext(snapshot("10700"), recentWithPrice("10000", 5), EVALUATED_AT); // +7% >= 3%*2

        assertThat(rule.evaluate(context))
                .get()
                .extracting(Anomaly::severity)
                .isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("변동이 임계치 미만이면 탐지하지 않는다")
    void ignoresSmallMove() {
        DetectionContext context =
                new DetectionContext(snapshot("10100"), recentWithPrice("10000", 5), EVALUATED_AT); // +1%

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    @DisplayName("비교할 과거 데이터가 없으면 탐지하지 않는다")
    void ignoresWhenNoHistory() {
        DetectionContext context = new DetectionContext(snapshot("10400"), List.of(), EVALUATED_AT);

        assertThat(rule.evaluate(context)).isEmpty();
    }
}
