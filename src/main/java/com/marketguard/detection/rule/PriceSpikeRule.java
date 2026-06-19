package com.marketguard.detection.rule;

import com.marketguard.config.PriceSpikeProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 단기 가격 급변동 탐지: 현재가가 직전 평균가 대비 thresholdPercent 이상 벗어나면 이상 신호로 본다.
 * 임계치의 2배를 넘으면 CRITICAL로 분류한다.
 */
@Component
public class PriceSpikeRule implements DetectionRule {

    private final PriceSpikeProperties props;

    public PriceSpikeRule(PriceSpikeProperties props) {
        this.props = props;
    }

    @Override
    public RuleType type() {
        return RuleType.PRICE_SPIKE;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        List<PriceSnapshot> recent = context.recent();
        if (recent.isEmpty()) {
            return Optional.empty();   // 비교할 과거 데이터가 아직 없음
        }
        double average = recent.stream()
                .limit(props.lookback())
                .mapToDouble(snapshot -> snapshot.getPrice().doubleValue())
                .average()
                .orElse(0);
        if (average <= 0) {
            return Optional.empty();
        }
        double current = context.current().getPrice().doubleValue();
        double changePercent = (current - average) / average * 100.0;

        if (Math.abs(changePercent) >= props.thresholdPercent()) {
            Severity severity = Math.abs(changePercent) >= props.thresholdPercent() * 2
                    ? Severity.CRITICAL
                    : Severity.WARNING;
            String message = "단기 가격 급변동: 현재가 %.2f (직전 평균 %.2f 대비 %+.2f%%)"
                    .formatted(current, average, changePercent);
            return Optional.of(Anomaly.of(
                    context.current().getStockCode(),
                    RuleType.PRICE_SPIKE,
                    severity,
                    message));
        }
        return Optional.empty();
    }
}
