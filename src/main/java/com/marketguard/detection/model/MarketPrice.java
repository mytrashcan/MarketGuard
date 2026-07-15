package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** A validated KRX market price at the exchange-provided observation time. */
public record MarketPrice(String stockCode, BigDecimal price, Instant capturedAt) {

    private static final Pattern KRX_SYMBOL = Pattern.compile("\\d{6}");

    public MarketPrice {
        if (stockCode == null || !KRX_SYMBOL.matcher(stockCode).matches()) {
            throw new IllegalArgumentException("stockCode must be a six-digit KRX symbol");
        }
        Objects.requireNonNull(price, "price must not be null");
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }
}
