package com.marketguard.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 단기 가격 급변동 탐지 룰 설정.
 * thresholdPercent: 직전 평균가 대비 변동률(%)이 이 값 이상이면 이상으로 본다.
 * lookback: 평균 계산에 사용할 직전 스냅샷 개수.
 */
@ConfigurationProperties(prefix = "detection.price-spike")
@Validated
public record PriceSpikeProperties(
        @DecimalMin(value = "0.0001") BigDecimal thresholdPercent,
        @Min(1) @Max(200) int lookback
) {
}
