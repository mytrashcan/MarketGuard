package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceVolumeSurgeRuleTest {

    private final PriceVolumeSurgeRule rule = new PriceVolumeSurgeRule(
            new BigDecimal("3"), 3, new BigDecimal("3"), 3);
    private final Instant now = Instant.parse("2026-07-15T01:00:00Z");

    @Test
    void detectsOnlyWhenBothPriceAndVolumeCrossTheirThresholds() {
        DetectionContext context = context("104", 400);

        Anomaly result = rule.evaluate(context).orElseThrow();

        assertThat(result.ruleType()).isEqualTo(RuleType.PRICE_VOLUME_SURGE);
        assertThat(result.evidence().measurements()).hasSize(3);
        assertThat(result.evidence().summary()).contains("4.00%", "4.0배");
    }

    @Test
    void doesNotDetectWhenOnlyOneComponentCrosses() {
        assertThat(rule.evaluate(context("102", 400))).isEmpty();
        assertThat(rule.evaluate(context("104", 200))).isEmpty();
    }

    private DetectionContext context(String price, long latestVolume) {
        MarketPrice current = new MarketPrice("005930", new BigDecimal(price), now);
        List<MarketPrice> recent = List.of(
                new MarketPrice("005930", new BigDecimal("100"), now.minusSeconds(30)),
                new MarketPrice("005930", new BigDecimal("100"), now.minusSeconds(60)),
                new MarketPrice("005930", new BigDecimal("100"), now.minusSeconds(90)));
        List<Candle> candles = List.of(
                candle(now.minusSeconds(180), 100), candle(now.minusSeconds(120), 100),
                candle(now.minusSeconds(60), latestVolume));
        return new DetectionContext(current, recent, null, null, candles, now);
    }

    private static Candle candle(Instant time, long volume) {
        return new Candle(time, new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("99"), new BigDecimal("100"), volume);
    }
}
