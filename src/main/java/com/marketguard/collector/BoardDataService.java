package com.marketguard.collector;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.model.Candle;
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
 * 시세 보드 데이터 조립 + 부가 데이터 캐시.
 * - 부가 데이터(전일 종가·종가·당일 거래량·최근 분봉)는 board.detail-refresh-ms 주기로 폴링해 캐싱.
 * - currentBoard(): 현재가(실시간) 배치 조회 후 카드 목록 조립. 장 마감 등으로 실시간가가 없으면
 *   캐시된 종가(lastClose)로 대체해서라도 화면을 채운다.
 */
@Slf4j
@Component
public class BoardDataService {

    private static final int MINI_CANDLE_COUNT = 30;

    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;
    private final Map<String, Detail> cache = new ConcurrentHashMap<>();

    /** 전일 종가(등락률 기준), 최신 종가(폴백 표시용), 당일 누적 거래량, 최근 분봉(미니 차트). */
    public record Detail(BigDecimal previousClose, BigDecimal lastClose, long volume, List<Candle> candles) {
    }

    public BoardDataService(TossApiProperties tossProps, TossMarketDataClient marketDataClient) {
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
    }

    public Detail detail(String symbol) {
        return cache.get(symbol);
    }

    /**
     * 현재 보드 카드 목록. 장중엔 실시간가, 장 마감 등 실시간가가 없으면 종가로 대체.
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
                    .forEach(snapshot -> live.put(snapshot.getStockCode(), snapshot.getPrice()));
        } catch (Exception e) {
            log.debug("실시간 시세 조회 실패 — 종가로 대체합니다: {}", e.getMessage());
        }
        return board.stream()
                .map(code -> BoardItem.of(code, live.get(code), cache.get(code)))
                .filter(item -> item.price() != null)   // 실시간가·종가 둘 다 없으면 제외
                .toList();
    }

    @Scheduled(fixedRateString = "${board.detail-refresh-ms}")
    public void refresh() {
        if (tossProps.clientId() == null || tossProps.clientId().isBlank()) {
            return;   // API 키 없으면 건너뜀
        }
        List<String> board = tossProps.watchList();
        if (board == null || board.isEmpty()) {
            return;
        }
        for (String symbol : board) {
            try {
                cache.put(symbol, fetchDetail(symbol));
            } catch (Exception e) {
                log.debug("[{}] 보드 상세 갱신 실패: {}", symbol, e.getMessage());
            }
        }
    }

    private Detail fetchDetail(String symbol) {
        // 일봉 2개로 전일 종가 + 최신 종가 + 당일 거래량 산출
        List<Candle> daily = marketDataClient.fetchCandles(symbol, "1d", 2).stream()
                .sorted(Comparator.comparing(Candle::timestamp))
                .toList();
        BigDecimal previousClose = null;
        BigDecimal lastClose = null;
        long volume = 0;
        if (!daily.isEmpty()) {
            Candle today = daily.get(daily.size() - 1);
            lastClose = today.close();
            volume = today.volume();
            previousClose = daily.size() >= 2 ? daily.get(daily.size() - 2).close() : today.open();
        }
        // 분봉으로 미니 캔들 차트
        List<Candle> candles = marketDataClient.fetchCandles(symbol, "1m", MINI_CANDLE_COUNT);
        return new Detail(previousClose, lastClose, volume, candles);
    }
}
