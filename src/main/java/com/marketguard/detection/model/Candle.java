package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

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
    public Candle {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        requirePositive(open, "open");
        requirePositive(high, "high");
        requirePositive(low, "low");
        requirePositive(close, "close");
        if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0
                || low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("candle OHLC values are inconsistent");
        }
        if (volume < 0) {
            throw new IllegalArgumentException("volume must not be negative");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
