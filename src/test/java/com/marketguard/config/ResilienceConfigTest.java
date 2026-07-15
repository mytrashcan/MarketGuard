package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.collector.client.TossApiException;
import io.github.resilience4j.core.functions.Either;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

class ResilienceConfigTest {

    private final TossHttpProperties properties = new TossHttpProperties(
            Duration.ofMillis(100), Duration.ofMillis(100), 3,
            Duration.ofMillis(1), Duration.ofMillis(10), 0.0, Duration.ofSeconds(2));
    private final ResilienceConfig config = new ResilienceConfig();

    @Test
    void doesNotRetryPermanentClientErrors() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> supplier = Retry.decorateSupplier(config.tossMarketRetry(properties), () -> {
            calls.incrementAndGet();
            throw new TossApiException(HttpStatus.BAD_REQUEST, null, "request-id");
        });

        assertThatThrownBy(supplier::get).isInstanceOf(TossApiException.class);
        assertThat(calls).hasValue(1);
    }

    @Test
    void retriesRateLimitsServerErrorsAndNetworkFailures() {
        assertRetryCount(new TossApiException(HttpStatus.TOO_MANY_REQUESTS, Duration.ZERO, "rate"), 3);
        assertRetryCount(new TossApiException(HttpStatus.SERVICE_UNAVAILABLE, null, "server"), 3);
        assertRetryCount(new ResourceAccessException("timeout"), 3);
    }

    @Test
    void retryAfterOverridesShorterExponentialDelay() {
        Retry retry = config.tossMarketRetry(properties);

        long delay = retry.getRetryConfig().<Object>getIntervalBiFunction().apply(
                1, Either.left(new TossApiException(
                        HttpStatus.TOO_MANY_REQUESTS, Duration.ofMillis(500), "rate")));

        assertThat(delay).isEqualTo(500);
    }

    private void assertRetryCount(RuntimeException failure, int expectedCalls) {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Object> supplier = Retry.decorateSupplier(config.tossMarketRetry(properties), () -> {
            calls.incrementAndGet();
            throw failure;
        });

        assertThatThrownBy(supplier::get).isSameAs(failure);
        assertThat(calls).hasValue(expectedCalls);
    }
}
