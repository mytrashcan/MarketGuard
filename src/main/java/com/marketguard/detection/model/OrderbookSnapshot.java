package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 호가창 스냅샷. bids=매수호가, asks=매도호가.
 */
public record OrderbookSnapshot(List<Level> bids, List<Level> asks) {

    public record Level(BigDecimal price, long volume) {
        public Level {
            Objects.requireNonNull(price, "price must not be null");
            if (price.signum() <= 0) {
                throw new IllegalArgumentException("price must be positive");
            }
            if (volume < 0) {
                throw new IllegalArgumentException("volume must not be negative");
            }
        }
    }

    public OrderbookSnapshot {
        bids = bids == null ? List.of() : List.copyOf(bids);
        asks = asks == null ? List.of() : List.copyOf(asks);
    }

    public long totalBidVolume() {
        return bids.stream().mapToLong(Level::volume).sum();
    }

    public long totalAskVolume() {
        return asks.stream().mapToLong(Level::volume).sum();
    }
}
