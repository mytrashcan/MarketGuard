package com.marketguard.collector.auth;

import com.marketguard.audit.Audited;
import com.marketguard.config.TossApiProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth2 토큰 발급 HTTP 호출. 토큰 발급은 보안상 중요한 작업이라 감사 로그(@Audited)를 남기고,
 * 회복탄력성(Retry + CircuitBreaker)도 적용한다.
 */
@Component
public class TossTokenClient {

    private final TossApiProperties props;
    private final RestClient authClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public TossTokenClient(TossApiProperties props, CircuitBreaker tossCircuitBreaker, Retry tossRetry) {
        this.props = props;
        this.authClient = RestClient.create();
        this.circuitBreaker = tossCircuitBreaker;
        this.retry = tossRetry;
    }

    @Audited("TOSS_TOKEN_ISSUE")
    public TokenResponse issue() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", props.clientId());
        form.add("client_secret", props.clientSecret());

        return circuitBreaker.executeSupplier(Retry.decorateSupplier(retry, () -> authClient.post()
                .uri(props.authUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class)));
    }
}
