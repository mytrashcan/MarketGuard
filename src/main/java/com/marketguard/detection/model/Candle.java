package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 캔들(OHLCV) 한 봉. 차트 표시 및 시가 기준 등락률·거래량 룰에 사용한다.
 */
public record Candle(
        Instant timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {
}
