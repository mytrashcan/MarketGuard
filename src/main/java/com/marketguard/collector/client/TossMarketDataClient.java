package com.marketguard.collector.client;

import com.marketguard.domain.marketdata.PriceSnapshot;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 Open API에서 시세를 조회한다.
 * GET /api/v1/prices?symbols=... 로 감시 대상 종목들을 한 번에 배치 조회한다.
 * (시세 API는 Bearer 토큰만 필요하고 계좌 헤더 X-Tossinvest-Account는 불필요하다)
 */
@Component
public class TossMarketDataClient {

    private final RestClient tossApiClient;

    public TossMarketDataClient(RestClient tossApiClient) {
        this.tossApiClient = tossApiClient;
    }

    /**
     * 여러 종목의 현재가를 한 번에 조회한다.
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
}
