package com.marketguard.collector;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Keeps the four main Toss market rankings warm without amplifying browser traffic upstream. */
@Slf4j
@Component
public class MarketRankingService {

    private static final int RANKING_SIZE = 100;

    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;
    private final StockReferenceService stockReferenceService;
    private final Map<MarketRankingType, List<MarketRankingQuote>> rankings = new ConcurrentHashMap<>();

    public MarketRankingService(
            TossApiProperties tossProps,
            TossMarketDataClient marketDataClient,
            StockReferenceService stockReferenceService) {
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
        this.stockReferenceService = stockReferenceService;
    }

    public List<MarketRankingQuote> current(MarketRankingType type) {
        return rankings.getOrDefault(type, List.of());
    }

    @Scheduled(
            fixedDelayString = "${board.ranking-refresh-ms:15000}",
            initialDelayString = "${board.ranking-initial-delay-ms:0}")
    public synchronized void refreshAll() {
        if (!hasCredentials()) {
            return;
        }
        List<String> symbols = new ArrayList<>();
        for (MarketRankingType type : MarketRankingType.values()) {
            try {
                List<MarketRankingQuote> refreshed = List.copyOf(
                        marketDataClient.fetchKrRanking(type, RANKING_SIZE));
                rankings.put(type, refreshed);
                refreshed.stream().map(MarketRankingQuote::stockCode).forEach(symbols::add);
            } catch (RuntimeException exception) {
                log.warn("토스 {} 랭킹 갱신 실패 — 마지막 정상 데이터를 유지합니다 ({})",
                        type, exception.getClass().getSimpleName());
            }
        }
        stockReferenceService.refreshMissing(symbols.stream().distinct().toList());
    }

    private boolean hasCredentials() {
        return tossProps.clientId() != null && !tossProps.clientId().isBlank()
                && tossProps.clientSecret() != null && !tossProps.clientSecret().isBlank();
    }
}
