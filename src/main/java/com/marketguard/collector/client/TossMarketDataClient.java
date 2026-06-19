package com.marketguard.collector.client;

import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.domain.marketdata.PriceSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 Open API에서 시세·호가·캔들·가격제한폭을 조회한다.
 * 모든 시세 API는 Bearer 토큰만 필요하고 계좌 헤더(X-Tossinvest-Account)는 불필요하다.
 */
@Component
public class TossMarketDataClient {

    private final RestClient tossApiClient;

    public TossMarketDataClient(RestClient tossApiClient) {
        this.tossApiClient = tossApiClient;
    }

    /**
     * 여러 종목의 현재가를 한 번에 배치 조회한다. GET /api/v1/prices?symbols=...
     */
    public List<PriceSnapshot> fetchPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        String symbolsParam = String.join(",", symbols);

        PricesResponse response = tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/prices")
                        .queryParam("symbols", symbolsParam)
                        .build())
                .retrieve()
                .body(PricesResponse.class);

        if (response == null || response.result() == null) {
            return List.of();
        }
        Instant capturedAt = Instant.now();
        return response.result().stream()
                .map(item -> new PriceSnapshot(item.symbol(), item.lastPrice(), capturedAt))
                .toList();
    }

    /** 단일 종목 가격제한폭(상·하한가). GET /api/v1/price-limits?symbol=... */
    public PriceLimit fetchPriceLimit(String symbol) {
        PriceLimitResponse response = tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/price-limits")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(PriceLimitResponse.class);
        return response == null ? null : response.toDomain();
    }

    /** 단일 종목 호가창. GET /api/v1/orderbook?symbol=... */
    public OrderbookSnapshot fetchOrderbook(String symbol) {
        OrderbookResponse response = tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/orderbook")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(OrderbookResponse.class);
        return response == null ? null : response.toDomain();
    }

    /** 단일 종목 캔들(OHLCV). GET /api/v1/candles?symbol=...&interval=1m&count=N */
    public List<Candle> fetchCandles(String symbol, String interval, int count) {
        CandlesResponse response = tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .body(CandlesResponse.class);
        return response == null ? List.of() : response.toDomain();
    }
}
