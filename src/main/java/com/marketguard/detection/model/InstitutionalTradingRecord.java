package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** One KRX market's daily institutional buy and sell totals. */
public record InstitutionalTradingRecord(
        String marketSymbol,
        LocalDate date,
        Instant updatedAt,
        InvestorTradingAmount institution,
        InstitutionalTradingBreakdown breakdown
) {
    public InstitutionalTradingRecord {
        if (!MarketInstrument.isMarket(marketSymbol)) {
            throw new IllegalArgumentException("marketSymbol must be KOSPI or KOSDAQ");
        }
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        Objects.requireNonNull(institution, "institution must not be null");
        Objects.requireNonNull(breakdown, "breakdown must not be null");
        if (institution.buyAmount().compareTo(breakdown.buyTotal()) != 0
                || institution.sellAmount().compareTo(breakdown.sellTotal()) != 0) {
            throw new IllegalArgumentException("institution totals must equal the seven-category breakdown");
        }
    }

    public BigDecimal netAmount() {
        return institution.netAmount();
    }
}
