package com.marketguard.detection.rule;

import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 거래량 급증 탐지: 현재 거래량이 직전 평균의 multiplier배 이상이면 이상 신호로 본다.
 */
@Component
public class VolumeSurgeRule implements DetectionRule {

    private final VolumeSurgeProperties props;

    public VolumeSurgeRule(VolumeSurgeProperties props) {
        this.props = props;
    }

    @Override
    public RuleType type() {
        return RuleType.VOLUME_SURGE;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        List<PriceSnapshot> recent = context.recent();
        if (recent.isEmpty()) {
            return Optional.empty();   // 비교할 과거 데이터가 아직 없음
        }
        double average = recent.stream()
                .limit(props.lookback())
                .mapToLong(PriceSnapshot::getVolume)
                .average()
                .orElse(0);
        if (average <= 0) {
            return Optional.empty();
        }
        long current = context.current().getVolume();
        if (current >= average * props.multiplier()) {
            String message = "거래량 급증: 현재 %d주 (직전 평균 %.0f주의 %.1f배)"
                    .formatted(current, average, current / average);
            return Optional.of(Anomaly.of(
                    context.current().getStockCode(),
                    RuleType.VOLUME_SURGE,
                    Severity.WARNING,
                    message));
        }
        return Optional.empty();
    }
}
