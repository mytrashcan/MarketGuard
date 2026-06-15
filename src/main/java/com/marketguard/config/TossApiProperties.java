package com.marketguard.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토스증권 Open API 연동 설정.
 * 시크릿(client-id/secret, account-id)은 코드가 아니라 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "toss")
public record TossApiProperties(
        String baseUrl,
        String authUrl,
        String clientId,
        String clientSecret,
        String accountId,
        List<String> watchList
) {
}
