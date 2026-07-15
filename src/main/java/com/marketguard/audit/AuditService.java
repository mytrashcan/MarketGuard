package com.marketguard.audit;

import com.marketguard.domain.audit.AuditLog;
import com.marketguard.domain.audit.AuditLogRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그를 별도 트랜잭션(REQUIRES_NEW)으로 기록한다.
 * 업무 트랜잭션이 롤백되더라도 감사 기록은 남도록 분리한다.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String detail, String outcome, long latencyMs) {
        auditLogRepository.save(new AuditLog(action, detail, outcome, latencyMs, clock.instant()));
    }
}
