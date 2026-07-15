package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
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
        return Optional.of(Anomaly.of(
                context.current().stockCode(),
                RuleType.ORDERBOOK_IMBALANCE,
                severity,
                message,
                context.evaluationTime()));
    }
}
