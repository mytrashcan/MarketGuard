package com.marketguard.collector.auth;

import com.marketguard.collector.client.TossApiException;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OAuth2 Client Credentials 액세스 토큰을 캐싱·갱신한다.
 * - 만료 직전(SAFETY_MARGIN)에 선제적으로 재발급
 * - double-checked locking으로 다중 스레드 동시 갱신을 1회로 제한(single-flight)
 * - 발급이 실패하면 30초부터 두 배씩(최대 10분, Retry-After가 더 길면 그만큼) 기다리는 동안 모든 호출을
 *   토스에 보내지 않고 바로 실패시킨다. 등록되지 않은 IP(403)처럼 재시도해도 소용없는 실패에
 *   여러 스케줄러가 쉬지 않고 발급을 요청해 차단을 키우지 않도록 하기 위해서다.
 * 실제 발급 HTTP 호출은 TossTokenClient(감사·회복탄력성 적용)에 위임한다.
 */
@Slf4j
@Component
public class TossTokenManager {

    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);
    static final Duration FIRST_BACKOFF = Duration.ofSeconds(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(10);
    /** Toss가 요청한 대기의 상한. 터무니없이 큰 Retry-After가 시각 계산을 넘치게 하거나 수집을 며칠씩 멈추지 않도록. */
    static final Duration MAX_REQUESTED_WAIT = Duration.ofHours(24);

    private final TossTokenClient tokenClient;
    private final Clock clock;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;
    /** 다음 발급 시도가 허용되는 시각. 아래 두 필드는 this 잠금 안에서만 바꾼다. */
    private Instant nextAttemptAt = Instant.EPOCH;
    private int consecutiveFailures;

    public TossTokenManager(TossTokenClient tokenClient, Clock clock) {
        this.tokenClient = tokenClient;
        this.clock = clock;
    }

    public String getAccessToken() {
        if (isValid()) {
            return cachedToken;
        }
        synchronized (this) {
            if (isValid()) {                 // 다른 스레드가 이미 갱신했는지 재확인
                return cachedToken;
            }
            if (clock.instant().isBefore(nextAttemptAt)) {
                throw new TossTokenUnavailableException(nextAttemptAt);
            }
            try {
                refresh();
            } catch (RuntimeException exception) {
                consecutiveFailures++;
                Duration wait = backoff(consecutiveFailures, exception);
                // 발급 호출 안의 재시도 대기가 길 수 있으므로, 대기 시간은 실패가 돌아온 시각부터 센다.
                nextAttemptAt = clock.instant().plus(wait);
                log.warn("토스 토큰 발급 실패({}회 연속, {}). {}초 동안 다시 요청하지 않습니다.",
                        consecutiveFailures, describe(exception), wait.toSeconds());
                throw exception;
            }
            consecutiveFailures = 0;
            nextAttemptAt = Instant.EPOCH;
            return cachedToken;
        }
    }

    /** 30초에서 시작해 실패할 때마다 두 배, 최대 10분. 토스가 Retry-After로 더 길게 요구하면 그 값(최대 24시간)을 따른다. */
    static Duration backoff(int consecutiveFailures, RuntimeException failure) {
        Duration exponential = FIRST_BACKOFF.multipliedBy(1L << Math.min(consecutiveFailures - 1, 20));
        Duration wait = exponential.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : exponential;
        if (failure instanceof TossApiException api && api.requestedRetryAfter() != null
                && api.requestedRetryAfter().compareTo(wait) > 0) {
            Duration requested = api.requestedRetryAfter();
            return requested.compareTo(MAX_REQUESTED_WAIT) > 0 ? MAX_REQUESTED_WAIT : requested;
        }
        return wait;
    }

    /** 원인을 알 수 있게 HTTP 상태만 남긴다(응답 본문과 자격 증명은 남기지 않는다). */
    private static String describe(RuntimeException failure) {
        return failure instanceof TossApiException api
                ? "HTTP " + api.status().value()
                : failure.getClass().getSimpleName();
    }

    private boolean isValid() {
        return cachedToken != null && clock.instant().isBefore(expiresAt);
    }

    private void refresh() {
        TokenResponse response = tokenClient.issue();
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("토스 토큰 응답이 비어 있습니다.");
        }
        if (response.tokenType() == null || !"Bearer".equalsIgnoreCase(response.tokenType())) {
            throw new IllegalStateException("토스 토큰 타입이 Bearer가 아닙니다.");
        }
        if (response.expiresIn() <= SAFETY_MARGIN.toSeconds()) {
            throw new IllegalStateException("토스 토큰 유효기간이 안전 여유시간보다 짧습니다.");
        }
        Instant now = clock.instant();
        Instant refreshedExpiresAt;
        try {
            refreshedExpiresAt = now.plusSeconds(response.expiresIn()).minus(SAFETY_MARGIN);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("토스 토큰 유효기간이 올바르지 않습니다.", exception);
        }
        this.cachedToken = response.accessToken();
        this.expiresAt = refreshedExpiresAt;
        log.info("토스 액세스 토큰 갱신 완료 (만료 예정: {})", expiresAt);
    }
}
