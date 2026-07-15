package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderbookImbalanceRuleTest {

    private final OrderbookImbalanceRule rule =
            new OrderbookImbalanceRule(new BigDecimal("3.0"));

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-15T00:00:00Z");

    private DetectionContext ctx(long bidVolume, long askVolume) {
        MarketPrice current = new MarketPrice("005930", new BigDecimal("70000"), EVALUATED_AT);
        OrderbookSnapshot orderbook = new OrderbookSnapshot(
                List.of(new OrderbookSnapshot.Level(new BigDecimal("69900"), bidVolume)),
                List.of(new OrderbookSnapshot.Level(new BigDecimal("70100"), askVolume)));
        return new DetectionContext(current, List.of(), null, orderbook, List.of(), EVALUATED_AT);
    }

    @Test
    @DisplayName("매수/매도 잔량 비율이 임계치 이상이면 탐지한다")
    void imbalance() {
        assertThat(rule.evaluate(ctx(10_000, 2_000)))   // 5배
                .get().extracting(Anomaly::ruleType).isEqualTo(RuleType.ORDERBOOK_IMBALANCE);
    }

    @Test
    @DisplayName("균형 잡힌 호가는 탐지하지 않는다")
    void balanced() {
        assertThat(rule.evaluate(ctx(1_000, 1_000))).isEmpty();
    }

    @Test
    @DisplayName("호가 데이터가 없으면 탐지하지 않는다")
    void noOrderbook() {
        MarketPrice current = new MarketPrice("005930", new BigDecimal("70000"), EVALUATED_AT);
        assertThat(rule.evaluate(new DetectionContext(current, List.of(), EVALUATED_AT))).isEmpty();
    }
}
