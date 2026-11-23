package com.marketguard.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "maintenance")
@Validated
/**
 * @param auditRetention 감사 로그 보존 기간. 0이면 지우지 않는다(기본값).
 */
public record MaintenanceProperties(
        @NotNull Duration snapshotCleanupInterval,
        @NotNull Duration snapshotRetention,
        Duration auditRetention
) {
    /** 감사 로그를 지우기 전 최소 보존 기간. 실수로 짧게 설정해 기록을 잃지 않도록 막는다. */
    public static final Duration MINIMUM_AUDIT_RETENTION = Duration.ofDays(1);

    public MaintenanceProperties {
        if (snapshotCleanupInterval != null && snapshotCleanupInterval.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("snapshot cleanup interval must be at least one second");
        }
        if (snapshotRetention != null && snapshotRetention.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("snapshot retention must be at least one minute");
        }
        if (auditRetention == null || auditRetention.isNegative()) {
            auditRetention = Duration.ZERO;
        }
        if (!auditRetention.isZero() && auditRetention.compareTo(MINIMUM_AUDIT_RETENTION) < 0) {
            throw new IllegalArgumentException("audit retention must be 0 (keep forever) or at least one day");
        }
    }
}
