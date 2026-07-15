package com.marketguard.collector.auth;

import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OAuth2 Client Credentials 액세스 토큰을 캐싱·갱신한다.
 * - 만료 직전(SAFETY_MARGIN)에 선제적으로 재발급
 * - double-checked locking으로 다중 스레드 동시 갱신을 1회로 제한(single-flight)
 * 실제 발급 HTTP 호출은 TossTokenClient(감사·회복탄력성 적용)에 위임한다.
 */
@Slf4j
@Component
public class TossTokenManager {

    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);

    private final TossTokenClient tokenClient;
    private final Clock clock;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

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
            refresh();
            return cachedToken;
        }
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
