package com.marketguard.collector.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TossTokenManagerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void cachesAValidToken() {
        TossTokenClient client = mock(TossTokenClient.class);
        when(client.issue()).thenReturn(new TokenResponse("token", "Bearer", 3600));
        TossTokenManager manager = new TossTokenManager(client, CLOCK);

        assertThat(manager.getAccessToken()).isEqualTo("token");
        assertThat(manager.getAccessToken()).isEqualTo("token");
    }

    @Test
    void rejectsLifetimeShorterThanTheSafetyMargin() {
        TossTokenClient client = mock(TossTokenClient.class);
        AtomicInteger calls = new AtomicInteger();
        when(client.issue()).thenAnswer(ignored ->
                new TokenResponse("token-" + calls.incrementAndGet(), "Bearer", 10));
        TossTokenManager manager = new TossTokenManager(client, CLOCK);

        assertThatThrownBy(manager::getAccessToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("유효기간");
        assertThat(calls).hasValue(1);
    }

    @Test
    void coalescesConcurrentRefreshesWithinOneProcess() throws Exception {
        TossTokenClient client = mock(TossTokenClient.class);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch releaseIssue = new CountDownLatch(1);
        when(client.issue()).thenAnswer(ignored -> {
            calls.incrementAndGet();
            assertThat(releaseIssue.await(5, TimeUnit.SECONDS)).isTrue();
            return new TokenResponse("shared-token", "Bearer", 3600);
        });
        TossTokenManager manager = new TossTokenManager(client, CLOCK);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 8)
                    .mapToObj(ignored -> (Callable<String>) manager::getAccessToken)
                    .toList();
            List<Future<String>> futures = tasks.stream().map(executor::submit).toList();
            releaseIssue.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("shared-token");
            }
        } finally {
            executor.shutdownNow();
        }
        assertThat(calls).hasValue(1);
    }
}
