package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 호가창 스냅샷. bids=매수호가, asks=매도호가.
 */
public record OrderbookSnapshot(List<Level> bids, List<Level> asks) {

    public record Level(BigDecimal price, long volume) {
    }

    public long totalBidVolume() {
        return bids == null ? 0 : bids.stream().mapToLong(Level::volume).sum();
    }

    public long totalAskVolume() {
        return asks == null ? 0 : asks.stream().mapToLong(Level::volume).sum();
    }
}
