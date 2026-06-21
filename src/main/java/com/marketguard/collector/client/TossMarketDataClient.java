package com.marketguard.collector.client;

import com.marketguard.collector.MarketDay;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.Warning;
import com.marketguard.domain.marketdata.PriceSnapshot;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 Open API에서 시세·호가·캔들·가격제한폭·거래캘린더를 조회한다.
 * 모든 호출은 Retry + CircuitBreaker(회복탄력성)로 감싼다.
 * 시세 API는 Bearer 토큰만 필요하고 계좌 헤더(X-Tossinvest-Account)는 불필요하다.
 */
@Component
public class TossMarketDataClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RestClient tossApiClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public TossMarketDataClient(RestClient tossApiClient, CircuitBreaker tossCircuitBreaker, Retry tossRetry) {
        this.tossApiClient = tossApiClient;
        this.circuitBreaker = tossCircuitBreaker;
        this.retry = tossRetry;
    }

    /** 재시도(inner) → 서킷브레이커(outer) 순으로 외부 호출을 보호한다. */
    private <T> T call(Supplier<T> supplier) {
        return circuitBreaker.executeSupplier(Retry.decorateSupplier(retry, supplier));
    }

    /** 여러 종목의 현재가를 한 번에 배치 조회한다. GET /api/v1/prices?symbols=... */
    public List<PriceSnapshot> fetchPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        String symbolsParam = String.join(",", symbols);

        PricesResponse response = call(() -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/prices")
                        .queryParam("symbols", symbolsParam)
                        .build())
                .retrieve()
                .body(PricesResponse.class));

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
        PriceLimitResponse response = call(() -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/price-limits")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(PriceLimitResponse.class));
        return response == null ? null : response.toDomain();
    }

    /** 단일 종목 호가창. GET /api/v1/orderbook?symbol=... */
    public OrderbookSnapshot fetchOrderbook(String symbol) {
        OrderbookResponse response = call(() -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/orderbook")
                        .queryParam("symbol", symbol)
                        .build())
                .retrieve()
                .body(OrderbookResponse.class));
        return response == null ? null : response.toDomain();
    }

    /** 단일 종목 캔들(OHLCV). GET /api/v1/candles?symbol=...&interval=1m&count=N */
    public List<Candle> fetchCandles(String symbol, String interval, int count) {
        CandlesResponse response = call(() -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval)
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .body(CandlesResponse.class));
        return response == null ? List.of() : response.toDomain();
    }

    /** 단일 종목의 거래소 지정 경고/주의 목록. GET /api/v1/stocks/{symbol}/warnings */
    public List<Warning> fetchWarnings(String symbol) {
        WarningsResponse response = call(() -> tossApiClient.get()
                .uri("/api/v1/stocks/{symbol}/warnings", symbol)
                .retrieve()
                .body(WarningsResponse.class));
        return response == null ? List.of() : response.toDomain();
    }

    /** 오늘의 KRX 개장 정보(휴장일·정규장 시간). GET /api/v1/market-calendar/KR */
    public Optional<MarketDay> fetchKrMarketToday() {
        MarketCalendarResponse response = call(() -> tossApiClient.get()
                .uri("/api/v1/market-calendar/KR")
                .retrieve()
                .body(MarketCalendarResponse.class));

        if (response == null || response.result() == null || response.result().today() == null) {
            return Optional.empty();
        }
        MarketCalendarResponse.Day today = response.result().today();
        LocalDate date = LocalDate.parse(today.date());
        MarketCalendarResponse.Integrated integrated = today.integrated();
        if (integrated == null || integrated.regularMarket() == null) {
            return Optional.of(new MarketDay(date, false, null, null));   // 휴장
        }
        MarketCalendarResponse.Session regular = integrated.regularMarket();
        LocalTime open = regular.startTime() != null
                ? regular.startTime().atZoneSameInstant(KST).toLocalTime() : null;
        LocalTime close = regular.endTime() != null
                ? regular.endTime().atZoneSameInstant(KST).toLocalTime() : null;
        return Optional.of(new MarketDay(date, true, open, close));
    }
}
