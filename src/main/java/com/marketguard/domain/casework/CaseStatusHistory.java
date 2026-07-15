package com.marketguard.domain.casework;

import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.ClosureReason;
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

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "case_status_history",
        indexes = @Index(name = "idx_case_history_case_time", columnList = "caseId,changedAt"))
public class CaseStatusHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long caseId;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private CaseStatus fromStatus;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CaseStatus toStatus;
    @Column(nullable = false, length = 120)
    private String actor;
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ClosureReason reason;
    @Column(length = 500)
    private String detail;
    @Column(nullable = false)
    private Instant changedAt;

    public CaseStatusHistory(Long caseId, CaseStatus fromStatus, CaseStatus toStatus,
                             String actor, ClosureReason reason, String detail, Instant changedAt) {
        this.caseId = caseId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.reason = reason;
        this.detail = detail;
        this.changedAt = changedAt;
    }
}
