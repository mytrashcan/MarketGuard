package com.marketguard.collector.auth;

import java.time.Duration;
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

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public TossTokenManager(TossTokenClient tokenClient) {
        this.tokenClient = tokenClient;
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
        return cachedToken != null && Instant.now().isBefore(expiresAt);
    }

    private void refresh() {
        TokenResponse response = tokenClient.issue();
        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("토스 토큰 응답이 비어 있습니다.");
        }
        this.cachedToken = response.accessToken();
        this.expiresAt = Instant.now()
                .plusSeconds(response.expiresIn())
                .minus(SAFETY_MARGIN);
        log.info("토스 액세스 토큰 갱신 완료 (만료 예정: {})", expiresAt);
    }
}
