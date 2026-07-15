package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
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
            return Optional.of(Anomaly.of(
                    context.current().stockCode(),
                    RuleType.PRICE_SPIKE,
                    severity,
                    message,
                    context.evaluationTime()));
        }
        return Optional.empty();
    }
}
