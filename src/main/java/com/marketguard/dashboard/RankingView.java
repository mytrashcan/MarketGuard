package com.marketguard.dashboard;

import com.marketguard.collector.MarketRankingQuote;
import com.marketguard.detection.model.DecimalMath;
import java.math.BigDecimal;
import java.time.Instant;

/** Browser-facing representation of one Toss market ranking row. */
public record RankingView(
        int rank,
        String code,
        String name,
        BigDecimal price,
        BigDecimal previousClose,
        BigDecimal changePercent,
        long volume,
        long tradingAmount,
        Instant rankedAt
) {
    public static RankingView from(MarketRankingQuote quote, String name) {
        BigDecimal changePercent = quote.changeRate() == null
                ? calculatedChange(quote)
                : quote.changeRate().multiply(BigDecimal.valueOf(100));
        return new RankingView(
                quote.rank(),
                quote.stockCode(),
                name,
                quote.lastPrice(),
                quote.basePrice(),
                changePercent,
                quote.tradingVolume(),
                quote.tradingAmount(),
                quote.rankedAt());
    }

    private static BigDecimal calculatedChange(MarketRankingQuote quote) {
        if (quote.lastPrice() == null || quote.lastPrice().signum() <= 0
                || quote.basePrice() == null || quote.basePrice().signum() <= 0) {
            return null;
        }
        return DecimalMath.percentageChange(quote.lastPrice(), quote.basePrice());
    }
}
