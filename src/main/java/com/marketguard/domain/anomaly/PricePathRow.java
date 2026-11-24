package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;

/** 내보내기용 가격 경로 한 점과 그 신호의 탐지 근거. */
public record PricePathRow(
        Long anomalyId,
        Instant detectedAt,
        String stockCode,
        RuleType ruleType,
        Severity severity,
        Direction direction,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        BigDecimal deviationRatio,
        Integer offsetMinutes,
        BigDecimal basePrice,
        BigDecimal price,
        BigDecimal marketChangePercent,
        BigDecimal sectorChangePercent
) {
}
