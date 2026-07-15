package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;
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
                    context.evaluationTime());
        }
        if (price.compareTo(limit.lowerLimit()) <= 0) {
            return anomaly(symbol, Severity.CRITICAL, "하한가 도달", price, limit.lowerLimit(),
                    context.evaluationTime());
        }

        if (proximityPercent.signum() > 0) {
            BigDecimal threshold = proximityPercent;
            BigDecimal upperDistance = DecimalMath.percentageChange(price, limit.upperLimit()).abs();
            BigDecimal lowerDistance = DecimalMath.percentageChange(price, limit.lowerLimit()).abs();
            if (upperDistance.compareTo(threshold) <= 0) {
                return anomaly(symbol, Severity.WARNING, "상한가 근접", price, limit.upperLimit(),
                        context.evaluationTime());
            }
            if (lowerDistance.compareTo(threshold) <= 0) {
                return anomaly(symbol, Severity.WARNING, "하한가 근접", price, limit.lowerLimit(),
                        context.evaluationTime());
            }
        }
        return Optional.empty();
    }

    private Optional<Anomaly> anomaly(String symbol, Severity severity, String label,
                                      BigDecimal price, BigDecimal limit, Instant detectedAt) {
        String message = "%s: 현재가 %s (제한가 %s)".formatted(label, price.toPlainString(), limit.toPlainString());
        return Optional.of(Anomaly.of(symbol, RuleType.PRICE_LIMIT, severity, message, detectedAt));
    }
}
