package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolumeSurgeRuleTest {

    private final VolumeSurgeRule rule = new VolumeSurgeRule(new VolumeSurgeProperties(3.0, 20));

    private Candle candle(int minuteOffset, long volume) {
        BigDecimal p = new BigDecimal("70000");
        return new Candle(Instant.now().plusSeconds(minuteOffset * 60L), p, p, p, p, volume);
    }

    private DetectionContext ctx(List<Candle> candles) {
        PriceSnapshot current = new PriceSnapshot("005930", new BigDecimal("70000"), Instant.now());
        return new DetectionContext(current, List.of(), null, null, candles);
    }

    @Test
    @DisplayName("최근 봉 거래량이 직전 평균의 N배 이상이면 탐지한다")
    void surge() {
        List<Candle> candles = List.of(
                candle(0, 100), candle(1, 100), candle(2, 100), candle(3, 100), candle(4, 1_000));
        assertThat(rule.evaluate(ctx(candles)))
                .get().extracting(Anomaly::ruleType).isEqualTo(RuleType.VOLUME_SURGE);
    }

    @Test
    @DisplayName("평이한 거래량은 탐지하지 않는다")
    void normal() {
        List<Candle> candles = List.of(
                candle(0, 100), candle(1, 100), candle(2, 120), candle(3, 110), candle(4, 130));
        assertThat(rule.evaluate(ctx(candles))).isEmpty();
    }

    @Test
    @DisplayName("봉이 1개 이하면 탐지하지 않는다")
    void tooFew() {
        assertThat(rule.evaluate(ctx(List.of(candle(0, 1_000))))).isEmpty();
    }
}
