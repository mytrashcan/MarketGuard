package com.marketguard.collector;

import com.marketguard.application.port.StockNameResolver;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 토스 종목 기본 정보의 한글 종목명을 캐시한다.
 * 종목 정보는 영업일 단위로 갱신되므로 전체 유니버스를 하루에 한 번만 새로 받는다.
 */
@Slf4j
@Component
public class StockReferenceService implements StockNameResolver {

    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;
    private final SymbolUniverse symbolUniverse;
    private final Map<String, String> names = new ConcurrentHashMap<>();

    public StockReferenceService(TossApiProperties tossProps,
                                 TossMarketDataClient marketDataClient,
                                 SymbolUniverse symbolUniverse) {
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
        this.symbolUniverse = symbolUniverse;
    }

    @Override
    public String nameOf(String stockCode) {
        return names.get(stockCode);
    }

    /** 보드 최초 표시 전에 아직 없는 종목명만 채운다. */
    public void refreshMissing(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        refreshSymbols(symbols, true);
    }

    @Scheduled(fixedDelayString = "${stock-reference.refresh-ms:86400000}")
    public void refreshAll() {
        Set<String> symbols = new LinkedHashSet<>(symbolUniverse.all());
        symbols.addAll(tossProps.watchList());
        refreshSymbols(List.copyOf(symbols), false);
    }

    private synchronized void refreshSymbols(List<String> symbols, boolean missingOnly) {
        if (symbols.isEmpty() || !hasCredentials()) {
            return;
        }
        List<String> requested = symbols.stream()
                .filter(symbol -> !missingOnly || !names.containsKey(symbol))
                .distinct()
                .toList();
        for (List<String> chunk : SymbolUniverse.chunk(requested, SymbolUniverse.MAX_PER_REQUEST)) {
            try {
                names.putAll(marketDataClient.fetchStockNames(chunk));
            } catch (RuntimeException exception) {
                log.warn("종목명 조회 실패({}종목): {}", chunk.size(), exception.getClass().getSimpleName());
            }
        }
    }

    private boolean hasCredentials() {
        return tossProps.clientId() != null && !tossProps.clientId().isBlank()
                && tossProps.clientSecret() != null && !tossProps.clientSecret().isBlank();
    }
}
