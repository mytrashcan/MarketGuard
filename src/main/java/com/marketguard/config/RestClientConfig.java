package com.marketguard.config;

import com.marketguard.collector.auth.TossTokenManager;
import com.marketguard.collector.client.TossApiErrorHandler;
import java.net.http.HttpClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 토스 API 호출용 RestClient.
 * 모든 시장데이터 요청에 Bearer 토큰을 자동으로 붙인다.
 * 토큰 발급 자체는 TossTokenManager가 별도 클라이언트로 처리하므로 순환참조가 없다.
 */
@Configuration
public class RestClientConfig {

    @Bean("tossApiClient")
    RestClient tossApiClient(TossApiProperties props, TossHttpProperties httpProperties,
                             TossTokenManager tokenManager, MeterRegistry meterRegistry) {
        return baseBuilder(httpProperties)
                .baseUrl(props.baseUrl())
                .requestInterceptor(new TossHttpMetricsInterceptor(meterRegistry, "market"))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(tokenManager.getAccessToken());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean("tossAuthClient")
    RestClient tossAuthClient(TossHttpProperties httpProperties, MeterRegistry meterRegistry) {
        return baseBuilder(httpProperties)
                .requestInterceptor(new TossHttpMetricsInterceptor(meterRegistry, "auth"))
                .build();
    }

    public RestClient.Builder baseBuilder(TossHttpProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultStatusHandler(HttpStatusCode::isError, new TossApiErrorHandler(properties));
    }
}
