package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.MarketContextTags;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 가격 변동과 거래량 증가가 같은 평가에서 모두 임계치를 넘는 복합 규칙. */
public class PriceVolumeSurgeRule implements DetectionRule {

    private static final Duration MAX_CANDLE_AGE = Duration.ofMinutes(5);
    private final BigDecimal priceThresholdPercent;
    private final int priceLookback;
    private final BigDecimal volumeMultiplier;
    private final int volumeLookback;
    private final Duration maxPriceSnapshotAge;

    public PriceVolumeSurgeRule(BigDecimal priceThresholdPercent, int priceLookback,
                                BigDecimal volumeMultiplier, int volumeLookback,
                                Duration maxPriceSnapshotAge) {
        if (priceThresholdPercent == null || priceThresholdPercent.signum() <= 0) {
            throw new IllegalArgumentException("priceThresholdPercent must be positive");
        }
        if (volumeMultiplier == null || volumeMultiplier.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("volumeMultiplier must be greater than one");
        }
        if (priceLookback < 1 || priceLookback > 200 || volumeLookback < 1 || volumeLookback > 200) {
            throw new IllegalArgumentException("lookbacks must be between 1 and 200");
        }
        if (maxPriceSnapshotAge == null || maxPriceSnapshotAge.isZero() || maxPriceSnapshotAge.isNegative()) {
            throw new IllegalArgumentException("maxPriceSnapshotAge must be positive");
        }
        this.priceThresholdPercent = priceThresholdPercent;
        this.priceLookback = priceLookback;
        this.volumeMultiplier = volumeMultiplier;
        this.volumeLookback = volumeLookback;
        this.maxPriceSnapshotAge = maxPriceSnapshotAge;
    }

    @Override
    public RuleType type() {
        return RuleType.PRICE_VOLUME_SURGE;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        java.time.Instant cutoff = context.evaluationTime().minus(maxPriceSnapshotAge);
        List<MarketPrice> fresh = context.recent().stream()
                .filter(price -> price.capturedAt() != null && !price.capturedAt().isBefore(cutoff))
                .toList();
        if (fresh.isEmpty() || context.candles().size() < 2) {
            return Optional.empty();
        }
        BigDecimal averagePrice = DecimalMath.average(fresh.stream()
                .limit(priceLookback).map(MarketPrice::price).toList());
        BigDecimal priceChange = DecimalMath.percentageChange(context.current().price(), averagePrice);
        if (priceChange.abs().compareTo(priceThresholdPercent) < 0) {
            return Optional.empty();
        }

        List<Candle> sorted = context.candles().stream()
                .sorted(Comparator.comparing(Candle::timestamp)).toList();
        Candle latest = sorted.get(sorted.size() - 1);
        Duration age = Duration.between(latest.timestamp(), context.evaluationTime());
        if (age.isNegative() || age.compareTo(MAX_CANDLE_AGE) > 0) {
            return Optional.empty();
        }
        List<Candle> previous = sorted.subList(0, sorted.size() - 1);
        BigDecimal averageVolume = DecimalMath.average(previous.stream()
                .skip(Math.max(0, previous.size() - volumeLookback))
                .map(candle -> BigDecimal.valueOf(candle.volume())).toList());
        if (averageVolume.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal volumeRatio = BigDecimal.valueOf(latest.volume()).divide(
                averageVolume, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
        if (volumeRatio.compareTo(volumeMultiplier) < 0) {
            return Optional.empty();
        }

        Direction direction = priceChange.signum() >= 0 ? Direction.UP : Direction.DOWN;
        Severity severity = priceChange.abs().compareTo(priceThresholdPercent.multiply(BigDecimal.valueOf(2))) >= 0
                && volumeRatio.compareTo(volumeMultiplier.multiply(BigDecimal.valueOf(2))) >= 0
                ? Severity.CRITICAL : Severity.WARNING;
        String message = "가격·거래량 동시 급증: 가격 %s%%, 거래량 %s배"
                .formatted(signed(priceChange), volumeRatio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString());
        AnomalyEvidence evidence = AnomalyEvidence.builder(
                        "가격·거래량 동시 급증",
                        "가격이 최근 평균 대비 %s%% 움직이는 동안 거래량이 %s배로 증가했습니다."
                                .formatted(signed(priceChange),
                                        volumeRatio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString()),
                        "가격과 거래량이 같은 시간에 각각의 기준을 넘었습니다. 단일 신호보다 우선 확인할 "
                                + "가치는 높지만 공개 이벤트나 시장 전체 움직임 때문에 발생할 수도 있습니다.")
                .values(priceChange, BigDecimal.ZERO, priceThresholdPercent,
                        priceChange.abs().divide(priceThresholdPercent, DecimalMath.CALCULATION_SCALE,
                                DecimalMath.ROUNDING_MODE), "%")
                .lookback("가격 최근 " + Math.min(priceLookback, context.recent().size())
                        + "개 스냅샷, 거래량 직전 " + Math.min(volumeLookback, previous.size()) + "개 1분봉")
                .direction(direction)
                .contextTags(MarketContextTags.at(context.evaluationTime(), "가격·거래량 동반 움직임"))
                .recommendedChecks(List.of(
                        "시장 지수와 동일 업종 종목도 함께 움직였는지 확인하세요.",
                        "관련 공시나 공개된 기업 이벤트가 있는지 확인하세요.",
                        "호가 불균형과 실제 체결 흐름이 같은 방향인지 확인하세요."))
                .marketObservedAt(latest.timestamp())
                .measurements(List.of(
                        new EvidenceMeasurement("price_change_percent", "최근 평균 대비 가격 변동률",
                                priceChange, BigDecimal.ZERO, priceThresholdPercent, null, "%"),
                        new EvidenceMeasurement("volume_ratio", "최근 평균 대비 거래량 배수",
                                volumeRatio, BigDecimal.ONE, volumeMultiplier, null, "배"),
                        new EvidenceMeasurement("latest_volume", "최근 1분 거래량",
                                BigDecimal.valueOf(latest.volume()), averageVolume, null, volumeRatio, "주")))
                .build();
        return Optional.of(Anomaly.explained(context.current().stockCode(), type(), severity,
                message, context.evaluationTime(), evidence));
    }

    private static String signed(BigDecimal value) {
        String displayed = DecimalMath.display(value).toPlainString();
        return value.signum() >= 0 ? "+" + displayed : displayed;
    }
}
