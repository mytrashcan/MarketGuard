package com.marketguard.collector.client;

import com.marketguard.collector.MarketDay;
import com.marketguard.collector.MarketRankingQuote;
import com.marketguard.collector.MarketRankingType;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.MarketInstrument;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.Warning;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 Open API에서 시세·호가·캔들·가격제한폭·거래캘린더를 조회한다.
 * 모든 호출은 공식 호출 그룹별 RateLimiter와 Retry, CircuitBreaker로 감싼다.
 * 시세 API는 Bearer 토큰만 필요하고 계좌 헤더(X-Tossinvest-Account)는 불필요하다.
 */
@Component
public class TossMarketDataClient {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(TossMarketDataClient.class);
    private static final int MAX_BATCH_SIZE = 200;
    private static final Pattern KRX_SYMBOL = Pattern.compile("\\d{6}");
    /** 종목명 조회용: 6자리 숫자 주식코드 또는 ETN/ETF처럼 4자리 숫자 + 2자리 영숫자(예: 0197W0). */
    private static final Pattern NAME_LOOKUP_SYMBOL = Pattern.compile("\\d{6}|\\d{4}[A-Z0-9]{2}");

    private final RestClient tossApiClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final RateLimiter marketDataRateLimiter;
    private final RateLimiter chartRateLimiter;
    private final RateLimiter stockRateLimiter;
    private final RateLimiter marketInfoRateLimiter;
    private final RateLimiter rankingRateLimiter;
    private final RateLimiter marketIndicatorRateLimiter;

    public TossMarketDataClient(
            @Qualifier("tossApiClient") RestClient tossApiClient,
            @Qualifier("tossMarketCircuitBreaker") CircuitBreaker tossCircuitBreaker,
            @Qualifier("tossMarketRetry") Retry tossRetry,
            @Qualifier("tossMarketDataRateLimiter") RateLimiter marketDataRateLimiter,
            @Qualifier("tossChartRateLimiter") RateLimiter chartRateLimiter,
            @Qualifier("tossStockRateLimiter") RateLimiter stockRateLimiter,
            @Qualifier("tossMarketInfoRateLimiter") RateLimiter marketInfoRateLimiter,
            @Qualifier("tossRankingRateLimiter") RateLimiter rankingRateLimiter,
            @Qualifier("tossMarketIndicatorRateLimiter") RateLimiter marketIndicatorRateLimiter) {
        this.tossApiClient = tossApiClient;
        this.circuitBreaker = tossCircuitBreaker;
        this.retry = tossRetry;
        this.marketDataRateLimiter = marketDataRateLimiter;
        this.chartRateLimiter = chartRateLimiter;
        this.stockRateLimiter = stockRateLimiter;
        this.marketInfoRateLimiter = marketInfoRateLimiter;
        this.rankingRateLimiter = rankingRateLimiter;
        this.marketIndicatorRateLimiter = marketIndicatorRateLimiter;
    }

    /** 재시도(inner) → 서킷브레이커(outer) 순으로 외부 호출을 보호한다. */
    private <T> T call(RateLimiter rateLimiter, Supplier<T> supplier) {
        Supplier<T> rateLimited = RateLimiter.decorateSupplier(rateLimiter, supplier);
        return circuitBreaker.executeSupplier(Retry.decorateSupplier(retry, rateLimited));
    }

    /**
     * 여러 종목의 현재가를 한 번에 배치 조회한다. GET /api/v1/prices?symbols=...
     * 유효하지 않은 종목코드는 전체 배치를 실패시키지 않고 건너뛰고 경고 로그를 남긴다.
     */
    public List<MarketPrice> fetchPrices(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<String> valid = symbols.stream()
                .filter(TossMarketDataClient::isValidSymbol)
                .toList();
        if (valid.size() < symbols.size()) {
            LOG.warn("Skipping {} invalid symbol(s) in price batch lookup", symbols.size() - valid.size());
        }
        if (valid.isEmpty()) {
            return List.of();
        }
        if (valid.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("symbols must contain up to " + MAX_BATCH_SIZE
                    + " six-digit KRX symbols");
        }
        String symbolsParam = String.join(",", valid);

        PricesResponse response = call(marketDataRateLimiter, () -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/prices")
                        .queryParam("symbols", symbolsParam)
                        .build())
                .retrieve()
                .body(PricesResponse.class));

        if (response == null || response.result() == null) {
            return List.of();
        }
        return response.result().stream()
                .map(this::toMarketPrice)
                .flatMap(Optional::stream)
                .toList();
    }

    /** 단일 종목 가격제한폭(상·하한가). GET /api/v1/price-limits?symbol=... */
    public PriceLimit fetchPriceLimit(String symbol) {
        requireSymbol(symbol);
        PriceLimitResponse response = call(marketDataRateLimiter, () -> tossApiClient.get()
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
        requireSymbol(symbol);
        OrderbookResponse response = call(marketDataRateLimiter, () -> tossApiClient.get()
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
        return fetchCandles(symbol, interval, count, null, null);
    }

    /** 수정주가 적용 여부를 명시해 캔들을 조회한다. */
    public List<Candle> fetchCandles(String symbol, String interval, int count, boolean adjusted) {
        return fetchCandles(symbol, interval, count, Boolean.valueOf(adjusted), null);
    }

    /** 지정 시각 이하의 캔들을 조회해 과거 사건 주변 차트를 구성한다. */
    public List<Candle> fetchCandlesBefore(String symbol, String interval, int count, Instant before) {
        Objects.requireNonNull(before, "before must not be null");
        return fetchCandles(symbol, interval, count, null, before);
    }

    private List<Candle> fetchCandles(
            String symbol, String interval, int count, Boolean adjusted, Instant before) {
        requireSymbol(symbol);
        if (!("1m".equals(interval) || "1d".equals(interval))) {
            throw new IllegalArgumentException("interval must be 1m or 1d");
        }
        if (count < 1 || count > 200) {
            throw new IllegalArgumentException("count must be between 1 and 200");
        }
        CandlesResponse response = call(chartRateLimiter, () -> tossApiClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/v1/candles")
                            .queryParam("symbol", symbol)
                            .queryParam("interval", interval)
                            .queryParam("count", count);
                    if (adjusted != null) {
                        builder.queryParam("adjusted", adjusted);
                    }
                    if (before != null) {
                        builder.queryParam("before", before);
                    }
                    return builder.build();
                })
                .retrieve()
                .body(CandlesResponse.class));
        return response == null ? List.of() : response.toDomain();
    }

    /** 국내 시장 랭킹을 조회한다. 상승·하락 랭킹은 토스 제약에 맞춰 1일 기준을 사용한다. */
    public List<MarketRankingQuote> fetchKrRanking(MarketRankingType type, int count) {
        Objects.requireNonNull(type, "type must not be null");
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        RankingResponse response = call(rankingRateLimiter, () -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/rankings")
                        .queryParam("type", type.name())
                        .queryParam("marketCountry", "KR")
                        .queryParam("duration", type.duration())
                        .queryParam("count", count)
                        .build())
                .retrieve()
                .body(RankingResponse.class));
        return response == null ? List.of() : response.toDomain();
    }

    /**
     * KOSPI or KOSDAQ market-wide institutional buy/sell amounts, newest daily record first.
     * The current-day record is provisional until the upstream finishes its end-of-day update.
     */
    public List<InstitutionalTradingRecord> fetchInstitutionalTrading(String marketSymbol, int count) {
        if (!MarketInstrument.isMarket(marketSymbol)) {
            throw new IllegalArgumentException("marketSymbol must be KOSPI or KOSDAQ");
        }
        if (count < 1 || count > 100) {
            throw new IllegalArgumentException("count must be between 1 and 100");
        }
        InvestorTradingResponse response = call(marketIndicatorRateLimiter, () -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/market-indicators/{symbol}/investor-trading")
                        .queryParam("interval", "1d")
                        .queryParam("count", count)
                        .build(marketSymbol))
                .retrieve()
                .body(InvestorTradingResponse.class));
        return response == null ? List.of() : response.toDomain(marketSymbol);
    }

    /** 국내 시장 실시간 거래량 상위 100종목의 공식 전일 기준가와 거래량을 조회한다. */
    public List<MarketRankingQuote> fetchKrRealtimeVolumeRanking() {
        return fetchKrRanking(MarketRankingType.MARKET_TRADING_VOLUME, 100);
    }

    /** 종목 기본 정보에서 공식 한글 종목명을 배치 조회한다. */
    public Map<String, String> fetchStockNames(List<String> symbols) {
        validateSymbols(symbols);
        if (symbols.isEmpty()) {
            return Map.of();
        }
        String symbolsParam = String.join(",", symbols);
        StockInfoResponse response = call(stockRateLimiter, () -> tossApiClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/stocks")
                        .queryParam("symbols", symbolsParam)
                        .build())
                .retrieve()
                .body(StockInfoResponse.class));
        return response == null ? Map.of() : response.toNames();
    }

    /** 단일 종목의 거래소 지정 경고/주의 목록. GET /api/v1/stocks/{symbol}/warnings */
    public List<Warning> fetchWarnings(String symbol) {
        requireSymbol(symbol);
        WarningsResponse response = call(stockRateLimiter, () -> tossApiClient.get()
                .uri("/api/v1/stocks/{symbol}/warnings", symbol)
                .retrieve()
                .body(WarningsResponse.class));
        return response == null ? List.of() : response.toDomain();
    }

    /** 오늘의 KRX 개장 정보(휴장일·정규장 시간). GET /api/v1/market-calendar/KR */
    public Optional<MarketDay> fetchKrMarketToday() {
        MarketCalendarResponse response = call(marketInfoRateLimiter, () -> tossApiClient.get()
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

    private Optional<MarketPrice> toMarketPrice(PricesResponse.PriceItem item) {
        try {
            if (item == null || item.timestamp() == null) {
                throw new IllegalArgumentException("missing market timestamp");
            }
            return Optional.of(new MarketPrice(item.symbol(), item.lastPrice(), item.timestamp().toInstant()));
        } catch (RuntimeException exception) {
            // Do not log the response body. Symbol is the only safe diagnostic field.
            String symbol = item == null ? "unknown" : item.symbol();
            org.slf4j.LoggerFactory.getLogger(TossMarketDataClient.class)
                    .warn("Discarding invalid price item for symbol {} ({})", symbol,
                            exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static void requireSymbol(String symbol) {
        if (!isValidSymbol(symbol)) {
            throw new IllegalArgumentException("symbol must be a six-digit KRX symbol");
        }
    }

    private static void validateSymbols(List<String> symbols) {
        if (symbols == null) {
            throw new IllegalArgumentException("symbols must not be null");
        }
        if (symbols.size() > 200 || symbols.stream().anyMatch(symbol -> !isValidNameLookupSymbol(symbol))) {
            throw new IllegalArgumentException("symbols must contain up to 200 six-digit KRX symbols or ETN/ETF symbols");
        }
    }

    private static boolean isValidSymbol(String symbol) {
        return symbol != null && KRX_SYMBOL.matcher(symbol).matches();
    }

    private static boolean isValidNameLookupSymbol(String symbol) {
        return symbol != null && NAME_LOOKUP_SYMBOL.matcher(symbol).matches();
    }
}
