package com.marketguard.audit;

import com.marketguard.config.MaintenanceProperties;
import com.marketguard.domain.audit.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * maintenance.audit-retention이 설정된 경우에만 오래된 감사 로그를 지운다(기본은 영구 보존).
 * 감사 기록을 지우는 일 자체도 감사 대상이라, 지운 건수와 기준 시각을 새 감사 기록으로 남긴다.
 */
@Slf4j
@Component
public class AuditRetentionScheduler {

    static final String ACTION = "AUDIT_LOG_PRUNED";

    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final MaintenanceProperties properties;
    private final Clock clock;

    public AuditRetentionScheduler(AuditLogRepository auditLogRepository,
                                   AuditService auditService,
                                   MaintenanceProperties properties,
                                   Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.properties = properties;
        this.clock = clock;
    }

    /** 지운 건수를 돌려준다. 보존 기간이 0이면 아무것도 하지 않는다. */
    @Transactional
    @Scheduled(fixedDelayString = "${maintenance.snapshot-cleanup-interval}")
    public int prune() {
        if (properties.auditRetention().isZero()) {
            return 0;
        }
        Instant cutoff = clock.instant().minus(properties.auditRetention());
        int deleted = auditLogRepository.deleteCreatedBefore(cutoff);
        if (deleted > 0) {
            auditService.record(ACTION, "deleted=%d before=%s".formatted(deleted, cutoff), "SUCCESS", 0L);
            log.info("보존 기간이 지난 감사 로그 {}건 정리", deleted);
        }
        return deleted;
    }
}
