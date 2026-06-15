package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * 토스 시세 응답 매핑 DTO.
 * ⚠️ 필드명/구조는 공식 OpenAPI 스펙에 맞춰 교체할 것:
 *    https://openapi.tossinvest.com/openapi-docs/latest/openapi.json
 */
public record PriceResponse(
        @JsonProperty("code") String code,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("volume") long volume
) {
}
