package com.marketguard.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 시세 수집 스케줄러 설정.
 * enabled=false면 API 키 없이도 앱이 정상 기동한다(수집만 건너뜀).
 */
@ConfigurationProperties(prefix = "collector")
@Validated
public record CollectorProperties(
        boolean enabled,
        @Min(1000) long pollIntervalMs
) {
}
