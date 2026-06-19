package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketguard.detection.model.Candle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * GET /api/v1/candles?symbol=...&interval=1m&count=N
 */
public record CandlesResponse(Result result) {

    public record Result(List<CandleData> candles) {
    }

    public record CandleData(
            @JsonProperty("timestamp") Instant timestamp,
            @JsonProperty("openPrice") BigDecimal openPrice,
            @JsonProperty("highPrice") BigDecimal highPrice,
            @JsonProperty("lowPrice") BigDecimal lowPrice,
            @JsonProperty("closePrice") BigDecimal closePrice,
            @JsonProperty("volume") long volume
    ) {
    }

    public List<Candle> toDomain() {
        if (result == null || result.candles() == null) {
            return List.of();
        }
        return result.candles().stream()
                .map(c -> new Candle(
                        c.timestamp(), c.openPrice(), c.highPrice(), c.lowPrice(), c.closePrice(), c.volume()))
                .toList();
    }
}
