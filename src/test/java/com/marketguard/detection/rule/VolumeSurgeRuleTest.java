package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VolumeSurgeRuleTest {

    private final VolumeSurgeRule rule =
            new VolumeSurgeRule(new VolumeSurgeProperties(3.0, 20));

    private PriceSnapshot snapshot(long volume) {
        return new PriceSnapshot("005930", new BigDecimal("70000"), volume, Instant.now());
    }

    private List<PriceSnapshot> recentWithVolume(long volume, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> snapshot(volume))
                .toList();
    }

    @Test
    @DisplayName("현재 거래량이 직전 평균의 multiplier배 이상이면 이상으로 탐지한다")
    void detectsSurge() {
        DetectionContext context =
                new DetectionContext(snapshot(1_000), recentWithVolume(100, 5));

        Optional<Anomaly> result = rule.evaluate(context);

        assertThat(result).isPresent();
        assertThat(result.get().ruleType()).isEqualTo(RuleType.VOLUME_SURGE);
    }

    @Test
    @DisplayName("임계치 미만이면 탐지하지 않는다")
    void ignoresNormalVolume() {
        DetectionContext context =
                new DetectionContext(snapshot(200), recentWithVolume(100, 5));

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    @DisplayName("비교할 과거 데이터가 없으면 탐지하지 않는다")
    void ignoresWhenNoHistory() {
        DetectionContext context = new DetectionContext(snapshot(1_000), List.of());

        assertThat(rule.evaluate(context)).isEmpty();
    }
}
