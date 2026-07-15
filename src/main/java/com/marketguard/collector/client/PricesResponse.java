package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 토스 시세 배치 응답: GET /api/v1/prices?symbols=005930,000660
 * 공식 timestamp를 탐지 시각의 근거로 보존한다. 그 밖의 미사용 필드는 무시한다.
 */
public record PricesResponse(List<PriceItem> result) {

    public record PriceItem(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("timestamp") OffsetDateTime timestamp,
            @JsonProperty("lastPrice") BigDecimal lastPrice,
            @JsonProperty("currency") String currency
    ) {
    }
}
