package com.marketguard.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import com.marketguard.collector.client.TossApiException;
import java.io.IOException;
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

    public static final String TOSS_MARKET = "tossMarketData";
    public static final String TOSS_AUTH = "tossAuth";

    @Bean("tossMarketDataRateLimiter")
    RateLimiter tossMarketDataRateLimiter() {
        return rateLimiter("tossMarketData", 10);
    }

    @Bean("tossChartRateLimiter")
    RateLimiter tossChartRateLimiter() {
        return rateLimiter("tossMarketChart", 5);
    }

    @Bean("tossStockRateLimiter")
    RateLimiter tossStockRateLimiter() {
        return rateLimiter("tossStock", 5);
    }

    @Bean("tossMarketInfoRateLimiter")
    RateLimiter tossMarketInfoRateLimiter() {
        return rateLimiter("tossMarketInfo", 3);
    }

    @Bean("tossRankingRateLimiter")
    RateLimiter tossRankingRateLimiter() {
        return rateLimiter("tossRanking", 3);
    }

    @Bean("tossAuthRateLimiter")
    RateLimiter tossAuthRateLimiter() {
        return rateLimiter("tossAuth", 5);
    }

    private RateLimiter rateLimiter(String name, int permitsPerSecond) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(permitsPerSecond)
                .timeoutDuration(Duration.ofSeconds(2))
                .build();
        return RateLimiter.of(name, config);
    }

    @Bean("tossMarketCircuitBreaker")
    CircuitBreaker tossMarketCircuitBreaker() {
        return circuitBreaker(TOSS_MARKET);
    }

    @Bean("tossAuthCircuitBreaker")
    CircuitBreaker tossAuthCircuitBreaker() {
        return circuitBreaker(TOSS_AUTH);
    }

    private CircuitBreaker circuitBreaker(String name) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)                       // 실패율 50% 초과면 OPEN
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(20)) // 20초 후 HALF_OPEN 시도
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(ResilienceConfig::isRetryable)
                .build();
        return CircuitBreaker.of(name, config);
    }

    @Bean("tossMarketRetry")
    public Retry tossMarketRetry(TossHttpProperties properties) {
        return retry(TOSS_MARKET, properties);
    }

    @Bean("tossAuthRetry")
    public Retry tossAuthRetry(TossHttpProperties properties) {
        return retry(TOSS_AUTH, properties);
    }

    private Retry retry(String name, TossHttpProperties properties) {
        IntervalFunction backoff = IntervalFunction.ofExponentialRandomBackoff(
                properties.initialBackoff(), 2.0, properties.jitterFactor(), properties.maxBackoff());
        RetryConfig config = RetryConfig.<Object>custom()
                .maxAttempts(properties.maxAttempts())
                .retryOnException(ResilienceConfig::isRetryable)
                .intervalBiFunction((attempt, outcome) -> {
                    long backoffMillis = backoff.apply(attempt);
                    if (outcome.isLeft() && outcome.getLeft() instanceof TossApiException exception
                            && exception.retryAfter() != null) {
                        return Math.max(backoffMillis, exception.retryAfter().toMillis());
                    }
                    return backoffMillis;
                })
                .build();
        return Retry.of(name, config);
    }

    static boolean isRetryable(Throwable throwable) {
        Throwable current = throwable;
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
            if (current instanceof TossApiException exception) {
                return exception.isRetryable();
            }
            if (current instanceof org.springframework.web.client.ResourceAccessException
                    || current instanceof IOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }
}
