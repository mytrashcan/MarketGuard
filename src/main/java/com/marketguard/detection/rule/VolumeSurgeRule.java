package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.MarketContextTags;
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
            BigDecimal thresholdRatio = actualRatio.divide(
                    multiplier, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
            BigDecimal priceChange = DecimalMath.percentageChange(latest.close(), latest.open());
            Direction direction = priceChange.signum() > 0 ? Direction.UP
                    : priceChange.signum() < 0 ? Direction.DOWN : Direction.NONE;
            String priceTag = direction == Direction.UP ? "가격 동반 상승"
                    : direction == Direction.DOWN ? "가격 동반 하락" : "가격 보합";
            AnomalyEvidence evidence = AnomalyEvidence.builder(
                            "거래량 급증",
                            "최근 1분 거래량이 직전 평균의 %s배입니다."
                                    .formatted(actualRatio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString()),
                            "평소보다 훨씬 많은 거래가 짧은 시간에 발생했습니다. "
                                    + "장 시작·마감 효과나 공개된 기업 이벤트 때문인지 확인해야 합니다.")
                    .values(BigDecimal.valueOf(latest.volume()), average, multiplier, actualRatio, "주")
                    .lookback("직전 " + Math.min(previous.size(), lookback) + "개 1분봉 평균")
                    .direction(direction)
                    .contextTags(MarketContextTags.at(context.evaluationTime(), priceTag))
                    .recommendedChecks(List.of(
                            "같은 시간의 가격 변동 방향과 폭을 확인하세요.",
                            "평소 같은 시간대에도 거래량이 높은 종목인지 확인하세요.",
                            "관련 공시나 공개된 기업 이벤트가 있는지 확인하세요."))
                    .marketObservedAt(latest.timestamp())
                    .measurements(List.of(
                            new EvidenceMeasurement("latest_volume", "최근 1분 거래량",
                                    BigDecimal.valueOf(latest.volume()), average, null, actualRatio, "주"),
                            new EvidenceMeasurement("volume_ratio", "평균 대비 거래량 배수",
                                    actualRatio, BigDecimal.ONE, multiplier, thresholdRatio, "배"),
                            new EvidenceMeasurement("candle_price_change", "같은 봉 가격 변동률",
                                    priceChange, BigDecimal.ZERO, null, null, "%")))
                    .build();
            return Optional.of(Anomaly.explained(
                    context.current().stockCode(),
                    RuleType.VOLUME_SURGE,
                    Severity.WARNING,
                    message,
                    context.evaluationTime(),
                    evidence));
        }
        return Optional.empty();
    }
}
