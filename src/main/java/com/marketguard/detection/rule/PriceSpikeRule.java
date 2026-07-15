package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.MarketContextTags;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 단기 가격 급변동 탐지: 현재가가 직전 평균가 대비 thresholdPercent 이상 벗어나면 이상 신호로 본다.
 * 임계치의 2배를 넘으면 CRITICAL로 분류한다.
 */
public class PriceSpikeRule implements DetectionRule {

    private final BigDecimal thresholdPercent;
    private final int lookback;

    public PriceSpikeRule(BigDecimal thresholdPercent, int lookback) {
        if (thresholdPercent == null || thresholdPercent.signum() <= 0) {
            throw new IllegalArgumentException("thresholdPercent must be positive");
        }
        if (lookback < 1 || lookback > 200) {
            throw new IllegalArgumentException("lookback must be between 1 and 200");
        }
        this.thresholdPercent = thresholdPercent;
        this.lookback = lookback;
    }

    @Override
    public RuleType type() {
        return RuleType.PRICE_SPIKE;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        List<MarketPrice> recent = context.recent();
        if (recent.isEmpty()) {
            return Optional.empty();   // 비교할 과거 데이터가 아직 없음
        }
        BigDecimal average = DecimalMath.average(recent.stream()
                .limit(lookback)
                .map(MarketPrice::price)
                .toList());
        BigDecimal current = context.current().price();
        BigDecimal changePercent = DecimalMath.percentageChange(current, average);
        BigDecimal threshold = thresholdPercent;

        if (changePercent.abs().compareTo(threshold) >= 0) {
            Severity severity = changePercent.abs().compareTo(threshold.multiply(BigDecimal.valueOf(2))) >= 0
                    ? Severity.CRITICAL
                    : Severity.WARNING;
            String signedChange = changePercent.signum() >= 0
                    ? "+" + DecimalMath.display(changePercent).toPlainString()
                    : DecimalMath.display(changePercent).toPlainString();
            String message = "단기 가격 급변동: 현재가 %s (직전 평균 %s 대비 %s%%)"
                    .formatted(current.toPlainString(), DecimalMath.display(average).toPlainString(), signedChange);
            BigDecimal thresholdRatio = changePercent.abs().divide(
                    threshold, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
            Direction direction = changePercent.signum() >= 0 ? Direction.UP : Direction.DOWN;
            AnomalyEvidence evidence = AnomalyEvidence.builder(
                            "단기 가격 급변동",
                            "현재가가 최근 평균보다 %s%% %s했습니다."
                                    .formatted(DecimalMath.display(changePercent.abs()).toPlainString(),
                                            direction == Direction.UP ? "상승" : "하락"),
                            "최근 가격 흐름과 비교해 주가가 짧은 시간에 빠르게 움직였습니다. "
                                    + "시장 전체 움직임이나 공개된 기업 이벤트의 영향인지 추가 확인이 필요합니다.")
                    .values(changePercent, BigDecimal.ZERO, threshold, thresholdRatio, "%")
                    .lookback("최근 " + Math.min(recent.size(), lookback) + "개 가격 스냅샷 평균")
                    .direction(direction)
                    .contextTags(MarketContextTags.at(context.evaluationTime(),
                            direction == Direction.UP ? "가격 상승" : "가격 하락"))
                    .recommendedChecks(List.of(
                            "같은 시간의 거래량 변화를 확인하세요.",
                            "시장 지수와 동일 업종 종목도 함께 움직였는지 확인하세요.",
                            "관련 공시나 공개된 기업 이벤트가 있는지 확인하세요."))
                    .marketObservedAt(context.current().capturedAt())
                    .measurements(List.of(
                            new EvidenceMeasurement("current_price", "현재가", current, null, null, null, "원"),
                            new EvidenceMeasurement("recent_average_price", "최근 평균가", average, null,
                                    null, null, "원"),
                            new EvidenceMeasurement("price_change_percent", "평균 대비 변동률", changePercent,
                                    BigDecimal.ZERO, threshold, thresholdRatio, "%")))
                    .build();
            return Optional.of(Anomaly.explained(
                    context.current().stockCode(),
                    RuleType.PRICE_SPIKE,
                    severity,
                    message,
                    context.evaluationTime(),
                    evidence));
        }
        return Optional.empty();
    }
}
