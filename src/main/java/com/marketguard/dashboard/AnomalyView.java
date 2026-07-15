package com.marketguard.dashboard;

import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRecord;
import java.time.Instant;

public record AnomalyView(
        String stockCode,
        RuleType ruleType,
        Severity severity,
        String message,
        Instant detectedAt
) {
    static AnomalyView from(AnomalyRecord record) {
        return new AnomalyView(record.getStockCode(), record.getRuleType(), record.getSeverity(),
                record.getMessage(), record.getDetectedAt());
    }
}
