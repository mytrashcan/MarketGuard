package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.MarketContextTags;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 호가 불균형 탐지: 매수/매도 총잔량 비율이 임계치 이상이면 이상 신호.
 * 임계치의 2배를 넘으면 CRITICAL로 분류한다.
 */
public class OrderbookImbalanceRule implements DetectionRule {

    private final BigDecimal ratioThreshold;

    public OrderbookImbalanceRule(BigDecimal ratioThreshold) {
        if (ratioThreshold == null || ratioThreshold.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("ratioThreshold must be greater than one");
        }
        this.ratioThreshold = ratioThreshold;
    }

    @Override
    public RuleType type() {
        return RuleType.ORDERBOOK_IMBALANCE;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        OrderbookSnapshot orderbook = context.orderbook();
        if (orderbook == null) {
            return Optional.empty();
        }
        long bidVolume = orderbook.totalBidVolume();
        long askVolume = orderbook.totalAskVolume();
        if (bidVolume <= 0 || askVolume <= 0) {
            return Optional.empty();
        }
        BigDecimal ratio = DecimalMath.ratio(Math.max(bidVolume, askVolume), Math.min(bidVolume, askVolume));
        BigDecimal threshold = ratioThreshold;
        if (ratio.compareTo(threshold) < 0) {
            return Optional.empty();
        }
        boolean buyHeavy = bidVolume > askVolume;
        Severity severity = ratio.compareTo(threshold.multiply(BigDecimal.valueOf(2))) >= 0
                ? Severity.CRITICAL : Severity.WARNING;
        String message = "호가 불균형(%s): 매수잔량 %d / 매도잔량 %d (%s배)"
                .formatted(buyHeavy ? "매수 우위" : "매도 우위", bidVolume, askVolume,
                        ratio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString());
        BigDecimal thresholdRatio = ratio.divide(
                threshold, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
        Direction direction = buyHeavy ? Direction.BUY : Direction.SELL;
        AnomalyEvidence evidence = AnomalyEvidence.builder(
                        "호가 잔량 불균형",
                        "%s 잔량이 반대편의 %s배입니다."
                                .formatted(buyHeavy ? "매수" : "매도",
                                        ratio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString()),
                        "사려는 대기 물량과 팔려는 대기 물량 중 한쪽이 과도하게 많습니다. "
                                + "호가는 취소될 수 있고 실제 체결을 뜻하지 않으므로 가격과 체결 흐름을 함께 확인해야 합니다.")
                .values(ratio, BigDecimal.ONE, threshold, thresholdRatio, "배")
                .lookback("탐지 시점 호가창 전체 잔량")
                .direction(direction)
                .contextTags(MarketContextTags.at(context.evaluationTime(),
                        buyHeavy ? "매수 잔량 우세" : "매도 잔량 우세"))
                .recommendedChecks(List.of(
                        "같은 방향으로 실제 체결과 가격이 움직이는지 확인하세요.",
                        "불균형이 여러 번 연속으로 지속되는지 확인하세요.",
                        "호가 취소나 일시적 유동성 부족 가능성을 고려하세요."))
                .marketObservedAt(context.orderbook().observedAt() != null
                        ? context.orderbook().observedAt() : context.current().capturedAt())
                .measurements(List.of(
                        new EvidenceMeasurement("total_bid_volume", "총 매수 잔량",
                                BigDecimal.valueOf(bidVolume), null, null, null, "주"),
                        new EvidenceMeasurement("total_ask_volume", "총 매도 잔량",
                                BigDecimal.valueOf(askVolume), null, null, null, "주"),
                        new EvidenceMeasurement("orderbook_ratio", "큰쪽/작은쪽 잔량 비율",
                                ratio, BigDecimal.ONE, threshold, thresholdRatio, "배")))
                .build();
        return Optional.of(Anomaly.explained(
                context.current().stockCode(),
                RuleType.ORDERBOOK_IMBALANCE,
                severity,
                message,
                context.evaluationTime(),
                evidence));
    }
}
