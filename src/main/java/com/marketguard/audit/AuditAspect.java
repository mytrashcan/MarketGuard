package com.marketguard.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * @Audited 메서드를 감싸 작업명·성공/실패·소요시간을 감사 로그로 남긴다(AOP).
 */
@Slf4j
@Aspect
@Component
public class AuditAspect {

    private final AuditService auditService;

    public AuditAspect(AuditService auditService) {
        this.auditService = auditService;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        long startNanos = System.nanoTime();
        String outcome = "SUCCESS";
        String detail = joinPoint.getSignature().toShortString();
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            outcome = "FAILURE";
            detail = detail + " - " + t.getClass().getSimpleName();
            throw t;
        } finally {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            try {
                auditService.record(audited.value(), detail, outcome, latencyMs);
            } catch (Exception e) {
                log.warn("감사 로그 기록 실패: {}", e.getMessage());
            }
            log.info("[AUDIT] {} {} ({}ms)", audited.value(), outcome, latencyMs);
        }
    }
}
