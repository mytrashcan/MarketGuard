package com.marketguard.collector;

/** Main dashboard ranking categories supported by the Toss ranking API. */
public enum MarketRankingType {
    MARKET_TRADING_AMOUNT("realtime"),
    MARKET_TRADING_VOLUME("realtime"),
    TOP_GAINERS("1d"),
    TOP_LOSERS("1d");

    private final String duration;

    MarketRankingType(String duration) {
        this.duration = duration;
    }

    public String duration() {
        return duration;
    }
}
