package com.marketguard.detection.rule;

import com.marketguard.config.PriceLimitProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 가격제한폭 도달/근접 탐지: 현재가가 상·하한가에 도달하면 CRITICAL,
 * proximityPercent 이내로 근접하면 WARNING.
 */
@Component
public class PriceLimitRule implements DetectionRule {

    private final PriceLimitProperties props;

    public PriceLimitRule(PriceLimitProperties props) {
        this.props = props;
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
        BigDecimal price = context.current().getPrice();
        String symbol = context.current().getStockCode();

        if (price.compareTo(limit.upperLimit()) >= 0) {
            return anomaly(symbol, Severity.CRITICAL, "상한가 도달", price, limit.upperLimit());
        }
        if (price.compareTo(limit.lowerLimit()) <= 0) {
            return anomaly(symbol, Severity.CRITICAL, "하한가 도달", price, limit.lowerLimit());
        }

        if (props.proximityPercent() > 0) {
            double p = price.doubleValue();
            double upper = limit.upperLimit().doubleValue();
            double lower = limit.lowerLimit().doubleValue();
            if (upper > 0 && (upper - p) / upper * 100.0 <= props.proximityPercent()) {
                return anomaly(symbol, Severity.WARNING, "상한가 근접", price, limit.upperLimit());
            }
            if (lower > 0 && (p - lower) / lower * 100.0 <= props.proximityPercent()) {
                return anomaly(symbol, Severity.WARNING, "하한가 근접", price, limit.lowerLimit());
            }
        }
        return Optional.empty();
    }

    private Optional<Anomaly> anomaly(String symbol, Severity severity, String label,
                                      BigDecimal price, BigDecimal limit) {
        String message = "%s: 현재가 %s (제한가 %s)".formatted(label, price.toPlainString(), limit.toPlainString());
        return Optional.of(Anomaly.of(symbol, RuleType.PRICE_LIMIT, severity, message));
    }
}
