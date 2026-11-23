package com.marketguard.domain.anomaly;

import com.marketguard.detection.evaluation.OutcomeStatus;
import com.marketguard.detection.evaluation.SignalOutcome;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Instant;

/** 신호 집계에 필요한 열만 담은 조회 결과. */
public record SignalStatRow(
        RuleType ruleType,
        Severity severity,
        String stockCode,
        Instant detectedAt,
        OutcomeStatus outcomeStatus,
        BigDecimal outcomeBasePrice,
        BigDecimal outcomePrice,
        BigDecimal outcomeChangePercent,
        Boolean outcomeFollowedDirection
) {
    /** 측정 전이면 null. */
    public SignalOutcome outcome() {
        return outcomeStatus == null ? null : new SignalOutcome(outcomeStatus, outcomeBasePrice, outcomePrice,
                outcomeChangePercent, outcomeFollowedDirection);
    }
}
