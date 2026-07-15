package com.marketguard.domain.casework;

import com.marketguard.detection.casework.AttentionLevel;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.CaseTransitionPolicy;
import com.marketguard.detection.casework.ClosureReason;
import com.marketguard.detection.casework.CompositeScore;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.domain.anomaly.StringListJsonConverter;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "surveillance_case", indexes = {
        @Index(name = "idx_case_last_detected", columnList = "lastDetectedAt"),
        @Index(name = "idx_case_stock_last", columnList = "stockCode,lastDetectedAt"),
        @Index(name = "idx_case_status_score", columnList = "status,score")
})
public class SurveillanceCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, length = 200)
    private String stockName;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AttentionLevel attentionLevel;

    @Column(nullable = false, length = 500)
    private String scoreExplanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus status;

    @Column(nullable = false)
    private int signalCount;

    @Column(nullable = false)
    private Instant firstDetectedAt;

    @Column(nullable = false)
    private Instant lastDetectedAt;

    private Instant closedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 120)
    private String reviewer;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ClosureReason closureReason;

    @Column(length = 500)
    private String closureDetail;

    @Convert(converter = StringListJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> contextTags;

    @Version
    @Column(nullable = false)
    private long version;

    public static SurveillanceCase create(
            Anomaly signal, String stockName, CompositeScore score, Instant now) {
        SurveillanceCase value = new SurveillanceCase();
        value.stockCode = signal.stockCode();
        value.stockName = validName(stockName, signal.stockCode());
        value.title = signal.evidence().title();
        value.summary = signal.evidence().summary();
        value.applyScore(score);
        value.status = CaseStatus.NEW;
        value.signalCount = 1;
        value.firstDetectedAt = signal.detectedAt();
        value.lastDetectedAt = signal.detectedAt();
        value.updatedAt = now;
        value.contextTags = signal.evidence().contextTags();
        return value;
    }

    public void merge(Anomaly signal, String resolvedName, CompositeScore newScore, Instant now) {
        this.stockName = validName(resolvedName, stockCode);
        this.title = signal.evidence().title();
        this.summary = signal.evidence().summary();
        this.signalCount++;
        this.lastDetectedAt = signal.detectedAt();
        this.contextTags = java.util.stream.Stream.concat(
                        this.contextTags.stream(), signal.evidence().contextTags().stream())
                .distinct().limit(20).toList();
        applyScore(newScore);
        this.updatedAt = now;
    }

    public void reactivate(Anomaly signal, String resolvedName, CompositeScore newScore, Instant now) {
        merge(signal, resolvedName, newScore, now);
        this.status = CaseStatus.NEW;
        this.closedAt = null;
        this.closureReason = null;
        this.closureDetail = null;
        this.reviewer = null;
    }

    public void transition(CaseTransitionPolicy policy, CaseStatus target, ClosureReason reason,
                           String detail, String actor, Instant now) {
        policy.validate(status, target, reason);
        this.status = target;
        this.reviewer = actor;
        this.closureReason = reason;
        this.closureDetail = detail;
        this.closedAt = target.isTerminal() ? now : null;
        this.updatedAt = now;
    }

    public void closeForInactivity(Instant now) {
        closeForInactivity(now, "설정된 비활성 시간 동안 새 신호가 없어 자동 종결");
    }

    public void closeForInactivity(Instant now, String detail) {
        if (!status.isTerminal()) {
            this.status = CaseStatus.CLOSED;
            this.closureReason = ClosureReason.INACTIVITY;
            this.closureDetail = detail;
            this.reviewer = "system";
            this.closedAt = now;
            this.updatedAt = now;
        }
    }

    private void applyScore(CompositeScore value) {
        this.score = value.value();
        this.attentionLevel = value.attentionLevel();
        this.scoreExplanation = value.explanation();
    }

    private static String validName(String name, String fallback) {
        return name == null || name.isBlank() ? fallback : name;
    }
}
