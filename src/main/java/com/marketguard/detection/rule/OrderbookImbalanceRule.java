package com.marketguard.detection.rule;

import com.marketguard.config.OrderbookImbalanceProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 호가 불균형 탐지: 매수/매도 총잔량 비율이 임계치 이상이면 이상 신호.
 * 임계치의 2배를 넘으면 CRITICAL로 분류한다.
 */
@Component
public class OrderbookImbalanceRule implements DetectionRule {

    private final OrderbookImbalanceProperties props;

    public OrderbookImbalanceRule(OrderbookImbalanceProperties props) {
        this.props = props;
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
        double ratio = (double) Math.max(bidVolume, askVolume) / Math.min(bidVolume, askVolume);
        if (ratio < props.ratioThreshold()) {
            return Optional.empty();
        }
        boolean buyHeavy = bidVolume > askVolume;
        Severity severity = ratio >= props.ratioThreshold() * 2 ? Severity.CRITICAL : Severity.WARNING;
        String message = "호가 불균형(%s): 매수잔량 %d / 매도잔량 %d (%.1f배)"
                .formatted(buyHeavy ? "매수 우위" : "매도 우위", bidVolume, askVolume, ratio);
        return Optional.of(Anomaly.of(
                context.current().getStockCode(),
                RuleType.ORDERBOOK_IMBALANCE,
                severity,
                message));
    }
}
