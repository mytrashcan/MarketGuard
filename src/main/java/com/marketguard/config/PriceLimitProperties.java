package com.marketguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가격제한폭 도달 탐지 룰 설정.
 * proximityPercent: 상·하한가에 이 % 이내로 근접하면 WARNING(0이면 근접 경고 비활성, 도달만 탐지).
 */
@ConfigurationProperties(prefix = "detection.price-limit")
public record PriceLimitProperties(double proximityPercent) {
}
