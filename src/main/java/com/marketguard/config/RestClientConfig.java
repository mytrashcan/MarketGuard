package com.marketguard.config;

import com.marketguard.collector.auth.TossTokenManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 토스 API 호출용 RestClient.
 * 모든 요청에 Bearer 토큰과 계좌 헤더(X-Tossinvest-Account)를 자동으로 붙인다.
 * 토큰 발급 자체는 TossTokenManager가 별도 클라이언트로 처리하므로 순환참조가 없다.
 */
@Configuration
public class RestClientConfig {

    public static final String ACCOUNT_HEADER = "X-Tossinvest-Account";

    @Bean
    RestClient tossApiClient(TossApiProperties props, TossTokenManager tokenManager) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenManager.getAccessToken());
                    if (props.accountId() != null && !props.accountId().isBlank()) {
                        request.getHeaders().add(ACCOUNT_HEADER, props.accountId());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
