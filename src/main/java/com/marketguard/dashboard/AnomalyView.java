package com.marketguard.dashboard;

import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import java.math.BigDecimal;
import java.util.List;
import com.marketguard.domain.anomaly.AnomalyRecord;
import java.time.Instant;

public record AnomalyView(
        Long id,
        Long caseId,
        String stockCode,
        String stockName,
        RuleType ruleType,
        Severity severity,
        String message,
        Instant detectedAt,
        String title,
        String summary,
        String explanation,
        BigDecimal observedValue,
        BigDecimal baselineValue,
        BigDecimal thresholdValue,
        BigDecimal deviationRatio,
        String valueUnit,
        String lookbackDescription,
        Direction direction,
        List<String> contextTags,
        List<String> recommendedChecks,
        Instant marketObservedAt,
        String caution,
        List<EvidenceMeasurement> measurements
) {
    static AnomalyView from(AnomalyRecord record, String stockName) {
        String resolvedName = stockName == null || stockName.isBlank() ? record.getStockName() : stockName;
        return new AnomalyView(record.getId(), record.getCaseId(), record.getStockCode(), resolvedName,
                record.getRuleType(), record.getSeverity(), record.getMessage(), record.getDetectedAt(),
                record.getTitle(), record.getSummary(), record.getExplanation(), record.getObservedValue(),
                record.getBaselineValue(), record.getThresholdValue(), record.getDeviationRatio(),
                record.getValueUnit(), record.getLookbackDescription(), record.getDirection(),
                record.getContextTags(), record.getRecommendedChecks(), record.getMarketObservedAt(),
                record.getCaution(), record.getMeasurements());
    }
}
