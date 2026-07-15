package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 거래량 급증 탐지(캔들 기반): 가장 최근 봉의 거래량이 직전 봉 평균의 multiplier배 이상이면 이상.
 * 단, 최근 봉이 오래됐으면(장 마감/공휴일 등 스테일) 오탐 방지를 위해 탐지하지 않는다.
 */
public class VolumeSurgeRule implements DetectionRule {

    private static final long STALE_MINUTES = 5;

    private final BigDecimal multiplier;
    private final int lookback;

    public VolumeSurgeRule(BigDecimal multiplier, int lookback) {
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("multiplier must be greater than one");
        }
        if (lookback < 1 || lookback > 200) {
            throw new IllegalArgumentException("lookback must be between 1 and 200");
        }
        this.multiplier = multiplier;
        this.lookback = lookback;
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
        if (latest.timestamp() != null
                && Duration.between(latest.timestamp(), context.evaluationTime()).toMinutes() > STALE_MINUTES) {
            return Optional.empty();   // 최근 봉이 오래됨(장 마감/공휴일 등) → 스테일 오탐 방지
        }
        List<Candle> previous = sorted.subList(0, sorted.size() - 1);

        BigDecimal average = DecimalMath.average(previous.stream()
                .skip(Math.max(0, previous.size() - lookback))
                .map(candle -> BigDecimal.valueOf(candle.volume()))
                .toList());
        if (average.signum() <= 0) {
            return Optional.empty();
        }
        if (BigDecimal.valueOf(latest.volume()).compareTo(average.multiply(multiplier)) >= 0) {
            BigDecimal actualRatio = BigDecimal.valueOf(latest.volume())
                    .divide(average, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
            String message = "거래량 급증: 최근 봉 %d주 (직전 평균 %s주의 %s배)"
                    .formatted(latest.volume(), average.setScale(0, DecimalMath.ROUNDING_MODE).toPlainString(),
                            actualRatio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString());
            return Optional.of(Anomaly.of(
                    context.current().stockCode(),
                    RuleType.VOLUME_SURGE,
                    Severity.WARNING,
                    message,
                    context.evaluationTime()));
        }
        return Optional.empty();
    }
}
