package com.marketguard.collector.client;

import com.marketguard.collector.MarketRankingQuote;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** GET /api/v1/rankings response shared by the supported market ranking types. */
record RankingResponse(Result result) {

    record Result(OffsetDateTime rankedAt, List<RankingItem> rankings) {
    }

    record RankingItem(int rank, String symbol, RankingPrice price, long tradingVolume, long tradingAmount) {
    }

    record RankingPrice(BigDecimal lastPrice, BigDecimal basePrice, BigDecimal changeRate) {
    }

    List<MarketRankingQuote> toDomain() {
        if (result == null || result.rankings() == null) {
            return List.of();
        }
        return result.rankings().stream()
                .filter(item -> item != null && item.symbol() != null && item.price() != null)
                .filter(item -> item.price().lastPrice() != null && item.price().basePrice() != null)
                .map(item -> new MarketRankingQuote(
                        item.rank(), item.symbol(), item.price().lastPrice(), item.price().basePrice(),
                        item.price().changeRate(), item.tradingVolume(), item.tradingAmount(),
                        result.rankedAt() == null ? null : result.rankedAt().toInstant()))
                .toList();
    }
}
