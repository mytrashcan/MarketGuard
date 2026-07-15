package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketguard.detection.model.OrderbookSnapshot;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/v1/orderbook?symbol=...
 */
public record OrderbookResponse(Result result) {

    public record Result(OffsetDateTime timestamp, List<Level> bids, List<Level> asks) {
    }

    public record Level(
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("volume") long volume
    ) {
    }

    public OrderbookSnapshot toDomain() {
        if (result == null) {
            return null;
        }
        return new OrderbookSnapshot(toLevels(result.bids()), toLevels(result.asks()),
                result.timestamp() == null ? null : result.timestamp().toInstant());
    }

    private static List<OrderbookSnapshot.Level> toLevels(List<Level> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .map(level -> new OrderbookSnapshot.Level(level.price(), level.volume()))
                .toList();
    }
}
