package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

/**
 * 토스 시세 배치 응답: GET /api/v1/prices?symbols=005930,000660
 * 응답에 포함된 timestamp 등 미사용 필드는 무시한다
 * (Spring 기본 ObjectMapper가 FAIL_ON_UNKNOWN_PROPERTIES=false).
 */
public record PricesResponse(List<PriceItem> result) {

    public record PriceItem(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("lastPrice") BigDecimal lastPrice,
            @JsonProperty("currency") String currency
    ) {
    }
}
