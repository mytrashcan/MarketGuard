package com.marketguard.detection.model;

import java.util.Set;

/** Symbols that can own a surveillance signal. */
public final class MarketInstrument {

    public static final String KOSPI = "KOSPI";
    public static final String KOSDAQ = "KOSDAQ";
    private static final Set<String> MARKET_SYMBOLS = Set.of(KOSPI, KOSDAQ);

    private MarketInstrument() {
    }

    public static boolean isSupported(String symbol) {
        return symbol != null && (symbol.matches("\\d{6}") || MARKET_SYMBOLS.contains(symbol));
    }

    public static boolean isMarket(String symbol) {
        return MARKET_SYMBOLS.contains(symbol);
    }

    public static String displayName(String symbol) {
        return switch (symbol) {
            case KOSPI -> "코스피 시장";
            case KOSDAQ -> "코스닥 시장";
            default -> symbol;
        };
    }
}
