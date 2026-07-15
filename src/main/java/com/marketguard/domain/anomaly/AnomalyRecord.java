package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
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

    @Column(nullable = false, length = 200)
    private String stockName;

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

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, length = 2_000)
    private String explanation;

    @Column(precision = 30, scale = 8)
    private BigDecimal observedValue;

    @Column(precision = 30, scale = 8)
    private BigDecimal baselineValue;

    @Column(precision = 30, scale = 8)
    private BigDecimal thresholdValue;

    @Column(precision = 30, scale = 8)
    private BigDecimal deviationRatio;

    @Column(length = 30)
    private String valueUnit;

    @Column(length = 200)
    private String lookbackDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Direction direction;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> contextTags;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> recommendedChecks;

    private Instant marketObservedAt;

    @Column(nullable = false, length = 500)
    private String caution;

    @Convert(converter = MeasurementListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<EvidenceMeasurement> measurements;

    public AnomalyRecord(String stockCode, RuleType ruleType, Severity severity, String message, Instant detectedAt) {
        this(new Anomaly(stockCode, ruleType, severity, message, detectedAt), stockCode, 0L);
    }

    public static AnomalyRecord from(Anomaly anomaly) {
        return new AnomalyRecord(anomaly, anomaly.stockCode(), 0L);
    }

    public static AnomalyRecord from(Anomaly anomaly, String stockName, Long caseId) {
        return new AnomalyRecord(anomaly, stockName, caseId);
    }

    private AnomalyRecord(Anomaly anomaly, String stockName, Long caseId) {
        AnomalyEvidence evidence = anomaly.evidence();
        this.stockCode = anomaly.stockCode();
        this.stockName = stockName == null || stockName.isBlank() ? anomaly.stockCode() : stockName;
        this.ruleType = anomaly.ruleType();
        this.severity = anomaly.severity();
        this.message = anomaly.message();
        this.detectedAt = anomaly.detectedAt();
        this.caseId = caseId;
        this.title = evidence.title();
        this.summary = evidence.summary();
        this.explanation = evidence.explanation();
        this.observedValue = evidence.observedValue();
        this.baselineValue = evidence.baselineValue();
        this.thresholdValue = evidence.thresholdValue();
        this.deviationRatio = evidence.deviationRatio();
        this.valueUnit = evidence.valueUnit();
        this.lookbackDescription = evidence.lookbackDescription();
        this.direction = evidence.direction();
        this.contextTags = evidence.contextTags();
        this.recommendedChecks = evidence.recommendedChecks();
        this.marketObservedAt = evidence.marketObservedAt();
        this.caution = evidence.caution();
        this.measurements = evidence.measurements();
    }

    public Anomaly toDomain() {
        AnomalyEvidence evidence = new AnomalyEvidence(title, summary, explanation, observedValue,
                baselineValue, thresholdValue, deviationRatio, valueUnit, lookbackDescription,
                direction, contextTags, recommendedChecks, marketObservedAt, caution, measurements);
        return Anomaly.explained(stockCode, ruleType, severity, message, detectedAt, evidence);
    }
}
