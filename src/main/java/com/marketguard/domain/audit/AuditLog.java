package com.marketguard.domain.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 감사 로그(누가 언제 어떤 작업을 했고 결과·소요시간은 무엇인지). 한 번 기록되면 변경하지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_at", columnList = "createdAt"))
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditLog(String action, String detail, String outcome, long latencyMs, Instant createdAt) {
        this.action = action;
        this.detail = detail;
        this.outcome = outcome;
        this.latencyMs = latencyMs;
        this.createdAt = createdAt;
    }
}
