package com.marketguard.application;

import com.marketguard.application.port.SectorLookup;
import com.marketguard.detection.evaluation.MarketBenchmark;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/** 같은 구간의 시장 기준(스캔 종목 전체)과 업종 기준(신호 종목을 뺀 같은 업종) 변동률 중앙값. */
final class BenchmarkMoves {

    /** 표본이 부족하면 해당 값은 null. */
    record Moves(BigDecimal market, BigDecimal sector) {
    }

    private final SectorLookup sectors;
    private final MarketBenchmark market;
    private final MarketBenchmark sector;

    BenchmarkMoves(SectorLookup sectors, int marketMinimumSamples, int sectorMinimumSamples) {
        this.sectors = sectors;
        this.market = new MarketBenchmark(marketMinimumSamples);
        this.sector = new MarketBenchmark(sectorMinimumSamples);
    }

    Moves between(String stockCode, Map<String, BigDecimal> basePrices, Map<String, BigDecimal> laterPrices) {
        BigDecimal marketChange = market.medianChangePercent(basePrices, laterPrices);
        BigDecimal sectorChange = sectors.sectorOf(stockCode)
                .map(name -> sector.medianChangePercent(sameSector(basePrices, name, stockCode), laterPrices))
                .orElse(null);
        return new Moves(marketChange, sectorChange);
    }

    private Map<String, BigDecimal> sameSector(Map<String, BigDecimal> prices, String sectorName, String excludedCode) {
        Map<String, BigDecimal> result = new HashMap<>();
        prices.forEach((code, price) -> {
            if (!code.equals(excludedCode) && sectors.sectorOf(code).filter(sectorName::equals).isPresent()) {
                result.put(code, price);
            }
        });
        return result;
    }
}
