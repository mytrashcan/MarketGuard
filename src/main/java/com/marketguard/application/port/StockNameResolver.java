package com.marketguard.application.port;

@FunctionalInterface
public interface StockNameResolver {
    String nameOf(String stockCode);
}
