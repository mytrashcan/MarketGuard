package com.marketguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 호가 불균형 탐지 룰 설정.
 * ratioThreshold: 매수/매도 총잔량 비율(큰쪽÷작은쪽)이 이 값 이상이면 이상.
 */
@ConfigurationProperties(prefix = "detection.orderbook-imbalance")
public record OrderbookImbalanceProperties(double ratioThreshold) {
}
