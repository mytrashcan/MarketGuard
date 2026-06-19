package com.marketguard.collector;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.detection.model.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 종목별 당일 시가(시작가)를 제공한다. 시가는 장중 고정이라 일자별로 1회만 조회해 캐싱한다.
 * 일봉(interval=1d, count=1)의 openPrice를 시가로 사용한다.
 */
@Slf4j
@Component
public class OpenPriceProvider {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TossMarketDataClient marketDataClient;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(LocalDate date, BigDecimal open) {
    }

    public OpenPriceProvider(TossMarketDataClient marketDataClient) {
        this.marketDataClient = marketDataClient;
    }

    /** 당일 시가. 알 수 없으면 null. */
    public BigDecimal openPrice(String symbol) {
        LocalDate today = LocalDate.now(KST);
        Cached cached = cache.get(symbol);
        if (cached != null && cached.date().equals(today)) {
            return cached.open();
        }
        BigDecimal open = fetchOpen(symbol);
        if (open != null) {
            cache.put(symbol, new Cached(today, open));
        }
        return open;
    }

    private BigDecimal fetchOpen(String symbol) {
        try {
            List<Candle> candles = marketDataClient.fetchCandles(symbol, "1d", 1);
            if (!candles.isEmpty()) {
                return candles.get(candles.size() - 1).open();
            }
        } catch (Exception e) {
            log.debug("[{}] 시가 조회 실패: {}", symbol, e.getMessage());
        }
        return null;
    }
}
