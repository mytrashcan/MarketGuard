package com.marketguard.collector;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 토스 시장 거래량 랭킹에 포함된 가격 기준 정보.
 * basePrice는 해당 랭킹 타입에서 공식적으로 제공하는 전일 기준가다.
 */
public record MarketRankingQuote(
        int rank,
        String stockCode,
        BigDecimal lastPrice,
        BigDecimal basePrice,
        BigDecimal changeRate,
        long tradingVolume,
        long tradingAmount,
        Instant rankedAt
) {
    public MarketRankingQuote(
            String stockCode, BigDecimal lastPrice, BigDecimal basePrice,
            BigDecimal changeRate, long tradingVolume, Instant rankedAt) {
        this(0, stockCode, lastPrice, basePrice, changeRate, tradingVolume, 0L, rankedAt);
    }

    public MarketRankingQuote(
            String stockCode, BigDecimal lastPrice, BigDecimal basePrice, long tradingVolume) {
        this(0, stockCode, lastPrice, basePrice, null, tradingVolume, 0L, null);
    }
}
