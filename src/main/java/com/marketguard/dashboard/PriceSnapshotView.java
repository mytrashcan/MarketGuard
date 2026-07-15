package com.marketguard.dashboard;

import com.marketguard.domain.marketdata.PriceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;

public record PriceSnapshotView(String stockCode, BigDecimal price, Instant capturedAt) {
    static PriceSnapshotView from(PriceSnapshot snapshot) {
        return new PriceSnapshotView(snapshot.getStockCode(), snapshot.getPrice(), snapshot.getCapturedAt());
    }
}
