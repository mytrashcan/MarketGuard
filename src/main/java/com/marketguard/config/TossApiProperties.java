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

    /** 시세 조회에 필요한 client id/secret이 모두 설정됐는지. 없으면 외부 호출을 건너뛴다. */
    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }
}
