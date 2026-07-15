package com.marketguard.alert;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.time.Instant;

/** 대시보드 WebSocket에 전달하는 종목명 포함 이상 신호. */
public record AnomalyNotification(
        String stockCode,
        String stockName,
        RuleType ruleType,
        Severity severity,
        String message,
        Instant detectedAt
) {
    static AnomalyNotification from(Anomaly anomaly, String stockName) {
        return new AnomalyNotification(
                anomaly.stockCode(), stockName, anomaly.ruleType(), anomaly.severity(),
                anomaly.message(), anomaly.detectedAt());
    }
}
