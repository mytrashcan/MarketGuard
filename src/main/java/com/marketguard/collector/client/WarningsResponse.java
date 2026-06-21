package com.marketguard.collector.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.marketguard.detection.model.Warning;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/v1/stocks/{symbol}/warnings — result는 경고 항목 배열(없으면 빈 배열).
 */
public record WarningsResponse(List<Item> result) {

    public record Item(
            @JsonProperty("warningType") String warningType,
            @JsonProperty("startDate") LocalDate startDate,
            @JsonProperty("endDate") LocalDate endDate
    ) {
    }

    public List<Warning> toDomain() {
        if (result == null) {
            return List.of();
        }
        return result.stream()
                .map(item -> new Warning(item.warningType(), item.startDate(), item.endDate()))
                .toList();
    }
}
