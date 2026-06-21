package com.marketguard.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 외부(토스) API 호출의 회복탄력성 설정.
 * - Retry: 일시적 오류 시 짧게 재시도(지수 백오프).
 * - CircuitBreaker: 실패율이 높으면 회로를 열어 한동안 호출을 차단(연쇄 장애 방지).
 * 코어 라이브러리를 직접 구성해(스타터/AOP 미사용) Boot 버전과 무관하게 동작한다.
 */
@Configuration
public class ResilienceConfig {

    public static final String TOSS = "tossApi";

    @Bean
    CircuitBreaker tossCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                       // 실패율 50% 초과면 OPEN
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(20)) // 20초 후 HALF_OPEN 시도
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        return CircuitBreaker.of(TOSS, config);
    }

    @Bean
    Retry tossRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(300))
                .retryExceptions(Exception.class)
                .build();
        return Retry.of(TOSS, config);
    }
}
