package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketguard.detection.model.PriceLimit;
import java.math.BigDecimal;

/**
 * GET /api/v1/price-limits?symbol=...
 */
public record PriceLimitResponse(Result result) {

    public record Result(
            @JsonProperty("upperLimitPrice") BigDecimal upperLimitPrice,
            @JsonProperty("lowerLimitPrice") BigDecimal lowerLimitPrice
    ) {
    }

    public PriceLimit toDomain() {
        if (result == null) {
            return null;
        }
        return new PriceLimit(result.upperLimitPrice(), result.lowerLimitPrice());
    }
}
