package com.marketguard.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 토스증권 Open API 연동 설정.
 * 시크릿(client-id/secret)은 코드가 아니라 환경변수로 주입한다.
 */
@ConfigurationProperties(prefix = "toss")
@Validated
public record TossApiProperties(
        @NotBlank String baseUrl,
        @NotBlank String authUrl,
        String clientId,
        String clientSecret,
        List<@Pattern(regexp = "\\d{6}") String> watchList
) {
    public TossApiProperties {
        watchList = watchList == null ? List.of() : List.copyOf(watchList);
    }
}
