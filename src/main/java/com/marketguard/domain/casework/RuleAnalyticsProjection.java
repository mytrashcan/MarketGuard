package com.marketguard.domain.casework;

import java.math.BigDecimal;

public interface RuleAnalyticsProjection {
    String getRuleType();
    long getOccurrenceCount();
    long getCaseCount();
    long getDismissedCount();
    long getEscalatedCount();
    BigDecimal getAverageReviewSeconds();
}
