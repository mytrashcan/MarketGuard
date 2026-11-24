package com.marketguard.collector.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.client.TossApiErrorHandler;
import com.marketguard.collector.client.TossApiException;
import com.marketguard.config.TossHttpProperties;
import java.time.Clock;
import java.time.Duration;
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
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.http.client.MockClientHttpResponse;

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

    /** 테스트가 시간을 앞으로 돌릴 수 있는 시계. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-07-15T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static TossApiException forbidden() {
        return new TossApiException(HttpStatusCode.valueOf(403), null, null);
    }

    @Test
    void doesNotCallTossAgainUntilTheBackoffAfterAFailedIssue() {
        TossTokenClient client = mock(TossTokenClient.class);
        when(client.issue()).thenThrow(forbidden());
        MovableClock clock = new MovableClock();
        TossTokenManager manager = new TossTokenManager(client, clock);

        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        // 여러 스케줄러가 곧바로 다시 요청해도 토스에는 보내지 않는다
        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossTokenUnavailableException.class);
        }
        verify(client, times(1)).issue();

        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        verify(client, times(2)).issue();
        // 두 번째 실패 뒤에는 60초를 기다린다
        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossTokenUnavailableException.class);
        verify(client, times(2)).issue();
    }

    @Test
    void aSuccessfulIssueClearsTheBackoff() {
        TossTokenClient client = mock(TossTokenClient.class);
        when(client.issue()).thenThrow(forbidden())
                .thenReturn(new TokenResponse("token", "Bearer", 60))
                .thenThrow(forbidden());
        MovableClock clock = new MovableClock();
        TossTokenManager manager = new TossTokenManager(client, clock);

        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        clock.advance(Duration.ofSeconds(30));
        assertThat(manager.getAccessToken()).isEqualTo("token");
        // 토큰이 만료돼 다시 실패하면 대기 시간은 처음(30초)부터 다시 센다
        clock.advance(Duration.ofSeconds(31));
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        clock.advance(Duration.ofSeconds(30));
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        verify(client, times(4)).issue();
    }

    @Test
    void countsTheCooldownFromWhenTheFailureReturns() {
        TossTokenClient client = mock(TossTokenClient.class);
        MovableClock clock = new MovableClock();
        // 발급 호출 안의 재시도 대기로 실패가 60초 뒤에야 돌아온다
        when(client.issue()).thenAnswer(ignored -> {
            clock.advance(Duration.ofSeconds(60));
            throw forbidden();
        });
        TossTokenManager manager = new TossTokenManager(client, clock);

        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        clock.advance(Duration.ofSeconds(1));
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossTokenUnavailableException.class);
        verify(client, times(1)).issue();
    }

    @Test
    void waitsAsLongAsTossAsksEvenWhenInCallRetriesAreCapped() throws Exception {
        TossHttpProperties http = new TossHttpProperties(Duration.ofSeconds(1), Duration.ofSeconds(1), 3,
                Duration.ofMillis(300), Duration.ofSeconds(5), 0.0, Duration.ofSeconds(30));
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set("Retry-After", "120");

        TossApiException thrown = catchThrowableOfType(TossApiException.class,
                () -> new TossApiErrorHandler(http).handle(mock(HttpRequest.class), response));

        assertThat(thrown.retryAfter()).isEqualTo(Duration.ofSeconds(30));
        assertThat(thrown.requestedRetryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(TossTokenManager.backoff(1, thrown)).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void backoffDoublesUpToTenMinutesAndHonoursALongerRetryAfter() {
        RuntimeException failure = forbidden();
        assertThat(TossTokenManager.backoff(1, failure)).isEqualTo(Duration.ofSeconds(30));
        assertThat(TossTokenManager.backoff(2, failure)).isEqualTo(Duration.ofMinutes(1));
        assertThat(TossTokenManager.backoff(6, failure)).isEqualTo(Duration.ofMinutes(10));
        assertThat(TossTokenManager.backoff(1000, failure)).isEqualTo(Duration.ofMinutes(10));
        TossApiException limited = new TossApiException(HttpStatusCode.valueOf(429), Duration.ofMinutes(3), null);
        assertThat(TossTokenManager.backoff(1, limited)).isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void anAbsurdRetryAfterIsCappedSoTheCooldownIsStillRecorded() {
        TossTokenClient client = mock(TossTokenClient.class);
        Duration huge = Duration.ofSeconds(Long.MAX_VALUE);
        when(client.issue()).thenThrow(new TossApiException(HttpStatusCode.valueOf(429), null, huge, null));
        MovableClock clock = new MovableClock();
        TossTokenManager manager = new TossTokenManager(client, clock);

        assertThat(TossTokenManager.backoff(1, new TossApiException(HttpStatusCode.valueOf(429), null, huge, null)))
                .isEqualTo(Duration.ofHours(24));
        // 원래의 토스 오류가 그대로 전달되고, 다음 호출은 토스에 보내지 않는다
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossApiException.class);
        assertThatThrownBy(manager::getAccessToken).isInstanceOf(TossTokenUnavailableException.class);
        verify(client, times(1)).issue();
    }
}
