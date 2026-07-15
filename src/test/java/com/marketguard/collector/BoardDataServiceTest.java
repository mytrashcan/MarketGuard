package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.OrderbookSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardDataServiceTest {

    private static final String SYMBOL = "005930";

    @Mock
    private TossMarketDataClient marketDataClient;

    @Mock
    private StockReferenceService stockReferenceService;

    private BoardDataService service;

    @BeforeEach
    void setUp() {
        TossApiProperties properties = new TossApiProperties(
                "https://example.test", "https://example.test/oauth2/token",
                "client-id", "client-secret", List.of(SYMBOL));
        service = new BoardDataService(properties, marketDataClient, stockReferenceService);
    }

    @Test
    void prefersOfficialRankingBasePriceAndShowsTheStockName() {
        when(marketDataClient.fetchKrRealtimeVolumeRanking()).thenReturn(List.of(
                new MarketRankingQuote(SYMBOL, decimal("72000"), decimal("70000"),
                        decimal("0.0286"), 123456L, Instant.parse("2026-07-15T01:00:00Z"))));
        when(marketDataClient.fetchOrderbook(SYMBOL)).thenReturn(orderbook(300, 700));
        when(marketDataClient.fetchPrices(List.of(SYMBOL))).thenReturn(List.of(
                new MarketPrice(SYMBOL, decimal("73500"), Instant.parse("2026-07-15T01:00:00Z"))));
        when(stockReferenceService.nameOf(SYMBOL)).thenReturn("삼성전자");

        service.refresh();
        List<BoardItem> result = service.currentBoard();

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("삼성전자");
            assertThat(item.price()).isEqualByComparingTo("72000");
            assertThat(item.previousClose()).isEqualByComparingTo("70000");
            assertThat(item.changePercent()).isEqualByComparingTo("2.8600");
            assertThat(item.changeSource()).isEqualTo(PriceChangeSource.TOSS_RANKING);
            assertThat(item.priceObservedAt()).isEqualTo(Instant.parse("2026-07-15T01:00:00Z"));
            assertThat(item.volume()).isEqualTo(123456L);
            assertThat(item.bidVolume()).isEqualTo(300L);
            assertThat(item.askVolume()).isEqualTo(700L);
            assertThat(item.orderbookStatus()).isEqualTo(OrderbookStatus.AVAILABLE);
        });
        verify(marketDataClient, never()).fetchCandles(SYMBOL, "1d", 2, false);
    }

    @Test
    void fallsBackToUnadjustedDailyCandlesOutsideTheRanking() {
        when(marketDataClient.fetchKrRealtimeVolumeRanking()).thenReturn(List.of());
        when(marketDataClient.fetchCandles(SYMBOL, "1d", 2, false)).thenReturn(List.of(
                candle("2026-07-14T00:00:00Z", "68000", "70000", "68000", "70000", 100),
                candle("2026-07-15T00:00:00Z", "71000", "72000", "70500", "71500", 200)));

        service.refresh();

        assertThat(service.detail(SYMBOL)).satisfies(detail -> {
            assertThat(detail.previousClose()).isEqualByComparingTo("70000");
            assertThat(detail.lastClose()).isEqualByComparingTo("71500");
            assertThat(detail.volume()).isEqualTo(200L);
            assertThat(detail.changeSource()).isEqualTo(PriceChangeSource.UNADJUSTED_DAILY_CANDLES);
        });
        verify(marketDataClient).fetchCandles(SYMBOL, "1d", 2, false);
    }

    @Test
    void distinguishesMissingOrderbookDataFromZeroVolumes() {
        when(marketDataClient.fetchKrRealtimeVolumeRanking()).thenReturn(List.of(
                new MarketRankingQuote(SYMBOL, decimal("72000"), decimal("70000"),
                        decimal("0.0286"), 123456L, Instant.parse("2026-07-15T01:00:00Z"))));
        when(marketDataClient.fetchOrderbook(SYMBOL))
                .thenReturn(new OrderbookSnapshot(List.of(), List.of(), Instant.parse("2026-07-15T01:00:01Z")));

        service.refresh();

        assertThat(service.detail(SYMBOL).orderbookStatus()).isEqualTo(OrderbookStatus.NO_DATA);
    }

    private static OrderbookSnapshot orderbook(long bidVolume, long askVolume) {
        return new OrderbookSnapshot(
                List.of(new OrderbookSnapshot.Level(decimal("70000"), bidVolume)),
                List.of(new OrderbookSnapshot.Level(decimal("70100"), askVolume)));
    }

    private static Candle candle(
            String timestamp, String open, String high, String low, String close, long volume) {
        return new Candle(Instant.parse(timestamp), decimal(open), decimal(high), decimal(low), decimal(close), volume);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
