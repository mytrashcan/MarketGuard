package com.marketguard.collector.auth;

import com.marketguard.config.TossApiProperties;
import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 Client Credentials 액세스 토큰을 발급·캐싱·갱신한다.
 * - 만료 직전(SAFETY_MARGIN)에 선제적으로 재발급
 * - double-checked locking으로 다중 스레드 동시 갱신을 1회로 제한(single-flight)
 */
@Slf4j
@Component
public class TossTokenManager {

    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(30);

    private final TossApiProperties props;
    private final RestClient authClient;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public TossTokenManager(TossApiProperties props) {
        this.props = props;
        this.authClient = RestClient.create();
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
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        TokenResponse response = authClient.post()
                .uri(props.authUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

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
