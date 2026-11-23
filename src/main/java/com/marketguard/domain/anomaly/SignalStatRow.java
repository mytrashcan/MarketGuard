package com.marketguard.domain.anomaly;

import com.marketguard.detection.evaluation.OutcomeStatus;
import com.marketguard.detection.evaluation.SignalOutcome;
import com.marketguard.detection.evaluation.SignalStrength;
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
        Boolean outcomeFollowedDirection,
        BigDecimal outcomeMarketChangePercent,
        BigDecimal outcomeExcessChangePercent,
        Boolean outcomeFollowedAfterMarket,
        Integer outcomeHorizonMinutes,
        BigDecimal observedValue,
        BigDecimal thresholdValue,
        BigDecimal deviationRatio
) {
    /** 임계 배수 구간. 강도를 비교할 수 없는 규칙이면 null. */
    public SignalStrength.Band strengthBand() {
        return SignalStrength.band(SignalStrength.thresholdMultiple(ruleType, observedValue, thresholdValue, deviationRatio));
    }

    /** 다른 측정 기준(horizon)으로 잰 결과인지. 측정 전이거나 기준이 기록되지 않았으면 false. */
    public boolean measuredWithOtherHorizon(long horizonMinutes) {
        return outcomeStatus != null && outcomeHorizonMinutes != null && outcomeHorizonMinutes != horizonMinutes;
    }

    /** 측정 전이면 null. */
    public SignalOutcome outcome() {
        return outcomeStatus == null ? null : new SignalOutcome(outcomeStatus, outcomeBasePrice, outcomePrice,
                outcomeChangePercent, outcomeFollowedDirection, outcomeMarketChangePercent,
                outcomeExcessChangePercent, outcomeFollowedAfterMarket);
    }
}
