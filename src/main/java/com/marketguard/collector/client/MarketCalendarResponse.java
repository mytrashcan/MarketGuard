package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/**
 * GET /api/v1/market-calendar/KR
 * today.integrated 가 null 이면 휴장일. 사용하지 않는 필드(preMarket/afterMarket, previous/nextBusinessDay 등)는 무시.
 */
public record MarketCalendarResponse(Result result) {

    public record Result(Day today) {
    }

    public record Day(String date, Integrated integrated) {
    }

    public record Integrated(Session regularMarket) {
    }

    public record Session(
            @JsonProperty("startTime") OffsetDateTime startTime,
            @JsonProperty("endTime") OffsetDateTime endTime
    ) {
    }
}
