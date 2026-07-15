package com.marketguard.config;

import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 가격제한폭 도달 탐지 룰 설정.
 * proximityPercent: 상·하한가에 이 % 이내로 근접하면 WARNING(0이면 근접 경고 비활성, 도달만 탐지).
 */
@ConfigurationProperties(prefix = "detection.price-limit")
@Validated
public record PriceLimitProperties(
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal proximityPercent) {
}
