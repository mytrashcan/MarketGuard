package com.marketguard.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Event metrics for retry, circuit-breaker, and client-side quota controls. */
@Component
public class TossResilienceObservability {

    public TossResilienceObservability(
            MeterRegistry registry,
            @Qualifier("tossMarketRetry") Retry marketRetry,
            @Qualifier("tossAuthRetry") Retry authRetry,
            @Qualifier("tossMarketCircuitBreaker") CircuitBreaker marketCircuitBreaker,
            @Qualifier("tossAuthCircuitBreaker") CircuitBreaker authCircuitBreaker,
            @Qualifier("tossMarketDataRateLimiter") RateLimiter marketDataRateLimiter,
            @Qualifier("tossChartRateLimiter") RateLimiter chartRateLimiter,
            @Qualifier("tossStockRateLimiter") RateLimiter stockRateLimiter,
            @Qualifier("tossMarketInfoRateLimiter") RateLimiter marketInfoRateLimiter,
            @Qualifier("tossAuthRateLimiter") RateLimiter authRateLimiter) {
        observeRetry(registry, "market", marketRetry);
        observeRetry(registry, "auth", authRetry);
        observeCircuit(registry, "market", marketCircuitBreaker);
        observeCircuit(registry, "auth", authCircuitBreaker);
        observeRateLimit(registry, "market_data", marketDataRateLimiter);
        observeRateLimit(registry, "chart", chartRateLimiter);
        observeRateLimit(registry, "stock", stockRateLimiter);
        observeRateLimit(registry, "market_info", marketInfoRateLimiter);
        observeRateLimit(registry, "auth", authRateLimiter);
    }

    private static void observeRetry(MeterRegistry registry, String client, Retry retry) {
        Counter counter = Counter.builder("marketguard.toss.retries")
                .description("Toss HTTP retry attempts")
                .tag("client", client)
                .register(registry);
        retry.getEventPublisher().onRetry(event -> counter.increment());
    }

    private static void observeCircuit(MeterRegistry registry, String client, CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher()
                .onStateTransition(event -> Counter.builder("marketguard.toss.circuit.transitions")
                        .description("Toss circuit-breaker state transitions")
                        .tag("client", client)
                        .tag("transition", event.getStateTransition().name().toLowerCase())
                        .register(registry)
                        .increment())
                .onCallNotPermitted(event -> Counter.builder("marketguard.toss.circuit.rejected")
                        .description("Toss calls rejected by an open circuit")
                        .tag("client", client)
                        .register(registry)
                        .increment());
    }

    private static void observeRateLimit(MeterRegistry registry, String group, RateLimiter rateLimiter) {
        Counter counter = Counter.builder("marketguard.toss.rate_limit.rejected")
                .description("Toss calls rejected by the local rate limiter")
                .tag("group", group)
                .register(registry);
        rateLimiter.getEventPublisher().onFailure(event -> counter.increment());
    }
}
