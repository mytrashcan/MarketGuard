package com.marketguard.collector.client;

import com.marketguard.domain.marketdata.PriceSnapshot;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 토스 Open API에서 시세를 조회한다.
 */
@Component
public class TossMarketDataClient {

    private final RestClient tossApiClient;

    public TossMarketDataClient(RestClient tossApiClient) {
        this.tossApiClient = tossApiClient;
    }

    /**
     * 단일 종목 현재 시세 조회.
     * ⚠️ 엔드포인트 경로는 공식 문서 기준으로 교체 필요.
     */
    public PriceSnapshot fetchPrice(String stockCode) {
        PriceResponse response = tossApiClient.get()
                .uri("/v1/market/stocks/{code}/price", stockCode)
                .retrieve()
                .body(PriceResponse.class);

        if (response == null) {
            throw new IllegalStateException("시세 응답이 비어 있습니다: " + stockCode);
        }
        return new PriceSnapshot(
                stockCode,
                response.price(),
                response.volume(),
                Instant.now());
    }
}
