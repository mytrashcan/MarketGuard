package com.marketguard.dashboard;

import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.RuleType;
import com.marketguard.domain.casework.RuleAnalyticsProjection;
import java.math.BigDecimal;

public record RuleAnalyticsView(
        RuleType ruleType,
        long occurrenceCount,
        long caseCount,
        BigDecimal dismissedPercent,
        BigDecimal escalatedPercent,
        BigDecimal averageReviewSeconds
) {
    static RuleAnalyticsView from(RuleAnalyticsProjection value) {
        return new RuleAnalyticsView(RuleType.valueOf(value.getRuleType()), value.getOccurrenceCount(),
                value.getCaseCount(), percentage(value.getDismissedCount(), value.getCaseCount()),
                percentage(value.getEscalatedCount(), value.getCaseCount()),
                value.getAverageReviewSeconds() == null ? null
                        : value.getAverageReviewSeconds().setScale(2, DecimalMath.ROUNDING_MODE));
    }

    private static BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2, DecimalMath.ROUNDING_MODE);
        }
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, DecimalMath.ROUNDING_MODE);
    }
}
