package com.marketguard.application.port;

import java.util.Optional;

/** 종목의 업종. 업종 정보를 제공하지 않으면 항상 비어 있다. */
@FunctionalInterface
public interface SectorLookup {
    Optional<String> sectorOf(String stockCode);
}
