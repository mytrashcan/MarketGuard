package com.marketguard.collector.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GET /api/v1/stocks 종목 기본 정보 응답. */
record StockInfoResponse(List<StockInfo> result) {

    record StockInfo(String symbol, String name) {
    }

    Map<String, String> toNames() {
        if (result == null) {
            return Map.of();
        }
        Map<String, String> names = new LinkedHashMap<>();
        result.stream()
                .filter(item -> item != null && item.symbol() != null)
                .filter(item -> item.name() != null && !item.name().isBlank())
                .forEach(item -> names.put(item.symbol(), item.name().trim()));
        return Map.copyOf(names);
    }
}
