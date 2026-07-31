package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketRankingServiceTest {

    @Mock
    private TossMarketDataClient marketDataClient;

    @Mock
    private StockReferenceService stockReferenceService;

    @Test
    void cachesEveryRankingAndRefreshesNamesOncePerDistinctSymbol() {
        MarketRankingService service = new MarketRankingService(
                properties("client-id", "client-secret"), marketDataClient, stockReferenceService);
        MarketRankingQuote quote = quote("005930");
        for (MarketRankingType type : MarketRankingType.values()) {
            when(marketDataClient.fetchKrRanking(type, 100)).thenReturn(List.of(quote));
        }

        service.refreshAll();

        for (MarketRankingType type : MarketRankingType.values()) {
            assertThat(service.current(type)).containsExactly(quote);
        }
        verify(stockReferenceService).refreshMissing(List.of("005930"));
    }

    @Test
    void keepsTheLastSuccessfulRankingWhenOneRefreshFails() {
        MarketRankingService service = new MarketRankingService(
                properties("client-id", "client-secret"), marketDataClient, stockReferenceService);
        MarketRankingQuote quote = quote("005930");
        when(marketDataClient.fetchKrRanking(MarketRankingType.MARKET_TRADING_AMOUNT, 100))
                .thenReturn(List.of(quote))
                .thenThrow(new IllegalStateException("upstream unavailable"));

        service.refreshAll();
        service.refreshAll();

        assertThat(service.current(MarketRankingType.MARKET_TRADING_AMOUNT)).containsExactly(quote);
    }

    @Test
    void skipsRefreshWithoutCredentials() {
        MarketRankingService service = new MarketRankingService(
                properties("", ""), marketDataClient, stockReferenceService);

        service.refreshAll();

        verifyNoInteractions(marketDataClient, stockReferenceService);
    }

    private static TossApiProperties properties(String clientId, String clientSecret) {
        return new TossApiProperties(
                "https://example.test", "https://example.test/oauth2/token",
                clientId, clientSecret, List.of());
    }

    private static MarketRankingQuote quote(String symbol) {
        return new MarketRankingQuote(
                1, symbol, new BigDecimal("72000"), new BigDecimal("70000"),
                new BigDecimal("0.0286"), 123456L, 8_888_888L,
                Instant.parse("2026-07-15T01:00:00Z"));
    }
}
