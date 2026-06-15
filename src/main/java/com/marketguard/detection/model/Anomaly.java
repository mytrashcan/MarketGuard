package com.marketguard.detection.model;

import java.time.Instant;

/**
 * 탐지된 이상 신호(도메인 값 객체). 영속 엔티티(AnomalyRecord)와 분리해 룰 로직을 순수하게 유지한다.
 */
public record Anomaly(
        String stockCode,
        RuleType ruleType,
        Severity severity,
        String message,
        Instant detectedAt
) {
    public static Anomaly of(String stockCode, RuleType ruleType, Severity severity, String message) {
        return new Anomaly(stockCode, ruleType, severity, message, Instant.now());
    }
}
