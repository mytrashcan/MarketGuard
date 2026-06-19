package com.marketguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 거래량 급증 탐지 룰 설정(캔들 기반).
 * multiplier: 직전 봉 평균 거래량 대비 N배 이상이면 이상.
 * lookback: 평균 계산에 사용할 직전 봉 개수.
 */
@ConfigurationProperties(prefix = "detection.volume-surge")
public record VolumeSurgeProperties(double multiplier, int lookback) {
}
