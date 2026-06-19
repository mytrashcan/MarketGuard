package com.marketguard.detection.rule;

import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 거래량 급증 탐지(캔들 기반): 가장 최근 봉의 거래량이 직전 봉 평균의 multiplier배 이상이면 이상.
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
        List<Candle> candles = context.candles();
        if (candles == null || candles.size() < 2) {
            return Optional.empty();   // 비교할 봉이 부족
        }
        List<Candle> sorted = candles.stream()
                .sorted(Comparator.comparing(Candle::timestamp))
                .toList();
        Candle latest = sorted.get(sorted.size() - 1);
        List<Candle> previous = sorted.subList(0, sorted.size() - 1);

        double average = previous.stream()
                .skip(Math.max(0, previous.size() - props.lookback()))
                .mapToLong(Candle::volume)
                .average()
                .orElse(0);
        if (average <= 0) {
            return Optional.empty();
        }
        if (latest.volume() >= average * props.multiplier()) {
            String message = "거래량 급증: 최근 봉 %d주 (직전 평균 %.0f주의 %.1f배)"
                    .formatted(latest.volume(), average, latest.volume() / average);
            return Optional.of(Anomaly.of(
                    context.current().getStockCode(),
                    RuleType.VOLUME_SURGE,
                    Severity.WARNING,
                    message));
        }
        return Optional.empty();
    }
}
