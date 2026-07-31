package com.marketguard.detection.model;

import java.time.Instant;
import java.util.Objects;

/**
 * 탐지된 이상 신호(도메인 값 객체). 영속 엔티티(AnomalyRecord)와 분리해 룰 로직을 순수하게 유지한다.
 */
public record Anomaly(
        String stockCode,
        RuleType ruleType,
        Severity severity,
        String message,
        Instant detectedAt,
        AnomalyEvidence evidence
) {
    public Anomaly {
        if (!MarketInstrument.isSupported(stockCode)) {
            throw new IllegalArgumentException(
                    "stockCode must be a six-digit KRX symbol, KOSPI, or KOSDAQ");
        }
        Objects.requireNonNull(ruleType, "ruleType must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        if (message.length() > 500) {
            throw new IllegalArgumentException("message must not exceed 500 characters");
        }
        Objects.requireNonNull(detectedAt, "detectedAt must not be null");
        evidence = evidence == null ? AnomalyEvidence.legacy(message, detectedAt) : evidence;
    }

    public Anomaly(String stockCode, RuleType ruleType, Severity severity, String message, Instant detectedAt) {
        this(stockCode, ruleType, severity, message, detectedAt, null);
    }

    public static Anomaly of(
            String stockCode, RuleType ruleType, Severity severity, String message, Instant detectedAt) {
        return new Anomaly(stockCode, ruleType, severity, message, detectedAt);
    }

    public static Anomaly explained(
            String stockCode, RuleType ruleType, Severity severity, String message, Instant detectedAt,
            AnomalyEvidence evidence) {
        return new Anomaly(stockCode, ruleType, severity, message, detectedAt, evidence);
    }
}
