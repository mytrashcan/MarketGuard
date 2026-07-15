package com.marketguard.collector;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.OrderbookSnapshot;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시세 보드(랭킹 테이블) 데이터 조립 + 부가 데이터 캐시.
 * - 부가 데이터(전일 기준가·종가·당일 거래량·호가 총잔량)는 board.detail-refresh-ms 주기로 폴링해 캐싱.
 * - currentBoard(): 현재가(실시간) 배치 조회 후 행 목록 조립. 장 마감 등 실시간가가 없으면 종가로 대체.
 */
@Slf4j
@Component
public class BoardDataService {

    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;
    private final StockReferenceService stockReferenceService;
    private final Map<String, Detail> cache = new ConcurrentHashMap<>();

    /** 전일 기준가(등락률 기준), 최신 종가(폴백용), 당일 거래량, 호가 총잔량(매수/매도 비율용). */
    public record Detail(BigDecimal previousClose, BigDecimal lastClose, long volume,
                         long bidVolume, long askVolume) {
    }

    public BoardDataService(TossApiProperties tossProps,
                            TossMarketDataClient marketDataClient,
                            StockReferenceService stockReferenceService) {
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
        this.stockReferenceService = stockReferenceService;
    }

    public Detail detail(String symbol) {
        return cache.get(symbol);
    }

    /**
     * 현재 보드 행 목록. 장중엔 실시간가, 장 마감 등 실시간가가 없으면 종가로 대체.
     * API 키가 없거나 watch-list가 비면 빈 목록.
     */
    public List<BoardItem> currentBoard() {
        if (tossProps.clientId() == null || tossProps.clientId().isBlank()) {
            return List.of();
        }
        List<String> board = tossProps.watchList();
        if (board == null || board.isEmpty()) {
            return List.of();
        }
        Map<String, BigDecimal> live = new HashMap<>();
        try {
            marketDataClient.fetchPrices(board)
                    .forEach(snapshot -> live.put(snapshot.stockCode(), snapshot.price()));
        } catch (Exception e) {
            log.debug("실시간 시세 조회 실패 — 종가로 대체합니다 ({})", e.getClass().getSimpleName());
        }
        return board.stream()
                .map(code -> BoardItem.of(
                        code, stockReferenceService.nameOf(code), live.get(code), cache.get(code)))
                .filter(item -> item.price() != null)   // 실시간가·종가 둘 다 없으면 제외
                .toList();
    }

    @Scheduled(fixedDelayString = "${board.detail-refresh-ms}")
    public void refresh() {
        if (tossProps.clientId() == null || tossProps.clientId().isBlank()) {
            return;   // API 키 없으면 건너뜀
        }
        List<String> board = tossProps.watchList();
        if (board == null || board.isEmpty()) {
            return;
        }
        stockReferenceService.refreshMissing(board);
        Map<String, MarketRankingQuote> ranking = fetchRanking();
        for (String symbol : board) {
            try {
                cache.put(symbol, fetchDetail(symbol, ranking.get(symbol)));
            } catch (Exception e) {
                log.debug("[{}] 보드 상세 갱신 실패: {}", symbol, e.getClass().getSimpleName());
            }
        }
    }

    private Detail fetchDetail(String symbol, MarketRankingQuote ranking) {
        BigDecimal previousClose;
        BigDecimal lastClose;
        long volume;
        if (hasUsableBasePrice(ranking)) {
            previousClose = ranking.basePrice();
            lastClose = ranking.lastPrice();
            volume = ranking.tradingVolume();
        } else {
            // 랭킹 100위 밖 종목은 기업행사 전후에도 실제 전일 종가를 보존하도록 무수정주가를 사용한다.
            List<Candle> daily = marketDataClient.fetchCandles(symbol, "1d", 2, false).stream()
                    .sorted(Comparator.comparing(Candle::timestamp))
                    .toList();
            previousClose = null;
            lastClose = null;
            volume = 0;
            if (!daily.isEmpty()) {
                Candle today = daily.get(daily.size() - 1);
                lastClose = today.close();
                volume = today.volume();
                previousClose = daily.size() >= 2 ? daily.get(daily.size() - 2).close() : today.open();
            }
        }
        // 호가 총잔량(매수/매도 비율 바용)
        long bidVolume = 0;
        long askVolume = 0;
        try {
            OrderbookSnapshot orderbook = marketDataClient.fetchOrderbook(symbol);
            if (orderbook != null) {
                bidVolume = orderbook.totalBidVolume();
                askVolume = orderbook.totalAskVolume();
            }
        } catch (Exception e) {
            log.debug("[{}] 호가 조회 실패: {}", symbol, e.getClass().getSimpleName());
        }
        return new Detail(previousClose, lastClose, volume, bidVolume, askVolume);
    }

    private Map<String, MarketRankingQuote> fetchRanking() {
        try {
            Map<String, MarketRankingQuote> ranking = new HashMap<>();
            marketDataClient.fetchKrRealtimeVolumeRanking()
                    .forEach(quote -> ranking.put(quote.stockCode(), quote));
            return ranking;
        } catch (RuntimeException exception) {
            log.warn("토스 기준가 랭킹 조회 실패 — 무수정 전일 종가로 대체합니다 ({})",
                    exception.getClass().getSimpleName());
            return Map.of();
        }
    }

    private static boolean hasUsableBasePrice(MarketRankingQuote ranking) {
        return ranking != null
                && ranking.basePrice() != null && ranking.basePrice().signum() > 0
                && ranking.lastPrice() != null && ranking.lastPrice().signum() > 0;
    }
}
