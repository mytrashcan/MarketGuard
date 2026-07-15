package com.marketguard.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 거래량 급증 탐지 룰 설정(캔들 기반).
 * multiplier: 직전 봉 평균 거래량 대비 N배 이상이면 이상.
 * lookback: 평균 계산에 사용할 직전 봉 개수.
 */
@ConfigurationProperties(prefix = "detection.volume-surge")
@Validated
public record VolumeSurgeProperties(
        @DecimalMin(value = "1.0", inclusive = false) BigDecimal multiplier,
        @Min(1) @Max(200) int lookback) {
}
