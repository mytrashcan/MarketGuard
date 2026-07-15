package com.marketguard.dashboard;

import com.marketguard.domain.audit.AuditLog;
import java.time.Instant;

public record AuditLogView(String action, String detail, String outcome, long latencyMs, Instant createdAt) {
    static AuditLogView from(AuditLog log) {
        return new AuditLogView(log.getAction(), log.getDetail(), log.getOutcome(), log.getLatencyMs(),
                log.getCreatedAt());
    }
}
