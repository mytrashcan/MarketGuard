package com.marketguard.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 애너테이션이 붙은 public 빈 메서드는 AuditAspect가 감사 로그(작업명·결과·소요시간)를 남긴다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** 감사 로그에 기록할 작업명(예: TOSS_TOKEN_ISSUE). */
    String value();
}
