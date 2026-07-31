package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockReferenceServiceTest {

    @Mock
    private TossMarketDataClient marketDataClient;

    @Mock
    private SymbolUniverse symbolUniverse;

    @Test
    void cachesOfficialNamesForTheWatchListAndDetectionUniverse() {
        TossApiProperties properties = new TossApiProperties(
                "https://example.test", "https://example.test/oauth2/token",
                "client-id", "client-secret", List.of("005930"));
        when(symbolUniverse.all()).thenReturn(List.of("000660", "005930"));
        when(marketDataClient.fetchStockNames(List.of("000660", "005930")))
                .thenReturn(Map.of("005930", "삼성전자", "000660", "SK하이닉스"));
        StockReferenceService service = new StockReferenceService(
                properties, marketDataClient, symbolUniverse);

        service.refreshAll();
        service.refreshMissing(List.of("005930", "000660"));

        assertThat(service.nameOf("005930")).isEqualTo("삼성전자");
        assertThat(service.nameOf("000660")).isEqualTo("SK하이닉스");
        verify(marketDataClient).fetchStockNames(List.of("000660", "005930"));
    }

    @Test
    void resolvesSupportedMarketIndicatorNamesWithoutAnUpstreamLookup() {
        StockReferenceService service = new StockReferenceService(
                new TossApiProperties(
                        "https://example.test", "https://example.test/oauth2/token",
                        "", "", List.of()),
                marketDataClient,
                symbolUniverse);

        assertThat(service.nameOf("KOSPI")).isEqualTo("코스피 시장");
        assertThat(service.nameOf("KOSDAQ")).isEqualTo("코스닥 시장");
    }

    @Test
    void filtersNonSixDigitSymbolsBeforeNameLookup() {
        TossApiProperties properties = new TossApiProperties(
                "https://example.test", "https://example.test/oauth2/token",
                "client-id", "client-secret", List.of());
        when(marketDataClient.fetchStockNames(List.of("005930", "069500")))
                .thenReturn(Map.of("005930", "삼성전자", "069500", "KODEX 200"));
        StockReferenceService service = new StockReferenceService(
                properties, marketDataClient, symbolUniverse);

        // 랭킹 응답에 섞일 수 있는 ETN 등 비6자리 심볼은 종목명 API 호출에서 제외되어야 한다.
        service.refreshMissing(List.of("005930", "0197W0", "069500"));

        assertThat(service.nameOf("005930")).isEqualTo("삼성전자");
        assertThat(service.nameOf("0197W0")).isNull();
        verify(marketDataClient).fetchStockNames(List.of("005930", "069500"));
    }
}
