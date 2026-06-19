package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.config.PriceLimitProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceLimitRuleTest {

    private final PriceLimitRule rule = new PriceLimitRule(new PriceLimitProperties(1.0));

    private DetectionContext ctx(String price, String upper, String lower) {
        PriceSnapshot current = new PriceSnapshot("005930", new BigDecimal(price), Instant.now());
        return new DetectionContext(current, List.of(),
                new PriceLimit(new BigDecimal(upper), new BigDecimal(lower)), null, List.of());
    }

    @Test
    @DisplayName("상한가 도달 시 CRITICAL로 탐지한다")
    void upperReached() {
        assertThat(rule.evaluate(ctx("13000", "13000", "7000")))
                .get().extracting(Anomaly::severity).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("상한가 1% 이내 근접 시 WARNING으로 탐지한다")
    void nearUpper() {
        assertThat(rule.evaluate(ctx("9950", "10000", "7000")))
                .get().extracting(Anomaly::severity).isEqualTo(Severity.WARNING);
    }

    @Test
    @DisplayName("중간 가격이면 탐지하지 않는다")
    void middle() {
        assertThat(rule.evaluate(ctx("9000", "10000", "7000"))).isEmpty();
    }

    @Test
    @DisplayName("가격제한폭 데이터가 없으면 탐지하지 않는다")
    void noLimit() {
        PriceSnapshot current = new PriceSnapshot("005930", new BigDecimal("9000"), Instant.now());
        assertThat(rule.evaluate(new DetectionContext(current, List.of()))).isEmpty();
    }
}
