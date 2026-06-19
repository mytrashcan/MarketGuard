package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketguard.detection.model.OrderbookSnapshot;
import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/v1/orderbook?symbol=...
 */
public record OrderbookResponse(Result result) {

    public record Result(List<Level> bids, List<Level> asks) {
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
        return new OrderbookSnapshot(toLevels(result.bids()), toLevels(result.asks()));
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
