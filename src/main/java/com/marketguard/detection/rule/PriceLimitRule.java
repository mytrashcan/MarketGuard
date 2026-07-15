package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.MarketContextTags;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 가격제한폭 도달/근접 탐지: 현재가가 상·하한가에 도달하면 CRITICAL,
 * proximityPercent 이내로 근접하면 WARNING.
 */
public class PriceLimitRule implements DetectionRule {

    private final BigDecimal proximityPercent;

    public PriceLimitRule(BigDecimal proximityPercent) {
        if (proximityPercent == null || proximityPercent.signum() < 0) {
            throw new IllegalArgumentException("proximityPercent must not be negative");
        }
        this.proximityPercent = proximityPercent;
    }

    @Override
    public RuleType type() {
        return RuleType.PRICE_LIMIT;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        PriceLimit limit = context.priceLimit();
        if (limit == null || limit.upperLimit() == null || limit.lowerLimit() == null) {
            return Optional.empty();
        }
        BigDecimal price = context.current().price();
        String symbol = context.current().stockCode();

        if (price.compareTo(limit.upperLimit()) >= 0) {
            return anomaly(symbol, Severity.CRITICAL, "상한가 도달", price, limit.upperLimit(),
                    context, Direction.UP);
        }
        if (price.compareTo(limit.lowerLimit()) <= 0) {
            return anomaly(symbol, Severity.CRITICAL, "하한가 도달", price, limit.lowerLimit(),
                    context, Direction.DOWN);
        }

        if (proximityPercent.signum() > 0) {
            BigDecimal threshold = proximityPercent;
            BigDecimal upperDistance = DecimalMath.percentageChange(price, limit.upperLimit()).abs();
            BigDecimal lowerDistance = DecimalMath.percentageChange(price, limit.lowerLimit()).abs();
            if (upperDistance.compareTo(threshold) <= 0) {
                return anomaly(symbol, Severity.WARNING, "상한가 근접", price, limit.upperLimit(),
                        context, Direction.UP);
            }
            if (lowerDistance.compareTo(threshold) <= 0) {
                return anomaly(symbol, Severity.WARNING, "하한가 근접", price, limit.lowerLimit(),
                        context, Direction.DOWN);
            }
        }
        return Optional.empty();
    }

    private Optional<Anomaly> anomaly(String symbol, Severity severity, String label,
                                      BigDecimal price, BigDecimal limit, DetectionContext context,
                                      Direction direction) {
        Instant detectedAt = context.evaluationTime();
        String message = "%s: 현재가 %s (제한가 %s)".formatted(label, price.toPlainString(), limit.toPlainString());
        BigDecimal distancePercent = DecimalMath.percentageChange(price, limit).abs();
        String summary = label.contains("도달")
                ? "현재가가 오늘의 " + (direction == Direction.UP ? "상한가" : "하한가") + "에 도달했습니다."
                : "현재가가 오늘의 " + (direction == Direction.UP ? "상한가" : "하한가")
                        + "에서 " + DecimalMath.display(distancePercent).toPlainString() + "% 이내입니다.";
        AnomalyEvidence evidence = AnomalyEvidence.builder(
                        label,
                        summary,
                        "현재가가 오늘 허용되는 최대 상승 또는 하락 범위에 도달했거나 가까워졌습니다. "
                                + "가격제한폭 접근은 큰 변동을 나타내지만 그 원인과 거래의 적정성을 설명하지는 않습니다.")
                .values(price, limit, proximityPercent, distancePercent, "원")
                .lookback("당일 거래소 가격제한폭")
                .direction(direction)
                .contextTags(MarketContextTags.at(detectedAt, "가격제한폭 접근"))
                .recommendedChecks(List.of(
                        "거래량과 호가 잔량이 함께 급변했는지 확인하세요.",
                        "관련 공시나 공개된 기업 이벤트를 확인하세요.",
                        "시장 전체 또는 동일 업종의 움직임과 비교하세요."))
                .marketObservedAt(context.current().capturedAt())
                .measurements(List.of(
                        new EvidenceMeasurement("current_price", "현재가", price, null, null, null, "원"),
                        new EvidenceMeasurement("price_limit", "해당 방향 제한가", limit, null,
                                null, null, "원"),
                        new EvidenceMeasurement("distance_to_limit", "제한가까지 거리",
                                distancePercent, BigDecimal.ZERO, proximityPercent, null, "%")))
                .build();
        return Optional.of(Anomaly.explained(
                symbol, RuleType.PRICE_LIMIT, severity, message, detectedAt, evidence));
    }
}
