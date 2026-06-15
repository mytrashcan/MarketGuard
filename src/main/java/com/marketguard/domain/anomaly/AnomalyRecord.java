package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 탐지된 이상 신호의 영속 기록(감사추적용). 한 번 기록되면 변경하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "anomaly_record",
        indexes = @Index(name = "idx_anomaly_time", columnList = "detectedAt")
)
public class AnomalyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Severity severity;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false)
    private Instant detectedAt;

    public AnomalyRecord(String stockCode, RuleType ruleType, Severity severity, String message, Instant detectedAt) {
        this.stockCode = stockCode;
        this.ruleType = ruleType;
        this.severity = severity;
        this.message = message;
        this.detectedAt = detectedAt;
    }

    public static AnomalyRecord from(Anomaly anomaly) {
        return new AnomalyRecord(
                anomaly.stockCode(),
                anomaly.ruleType(),
                anomaly.severity(),
                anomaly.message(),
                anomaly.detectedAt());
    }
}
