package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 캔들(OHLCV) 한 봉. 탐지 룰에는 종가/거래량/시각만 사용한다.
 */
public record Candle(Instant timestamp, BigDecimal close, long volume) {
}
