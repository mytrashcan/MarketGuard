package com.marketguard.detection.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Daily institutional-flow observations for one KRX market at an evaluation time. */
public record InstitutionalFlowContext(
        String marketSymbol,
        List<InstitutionalTradingRecord> records,
        Instant evaluationTime
) {
    public InstitutionalFlowContext {
        if (!MarketInstrument.isMarket(marketSymbol)) {
            throw new IllegalArgumentException("marketSymbol must be KOSPI or KOSDAQ");
        }
        records = records == null ? List.of() : List.copyOf(records);
        if (records.stream().anyMatch(record -> !marketSymbol.equals(record.marketSymbol()))) {
            throw new IllegalArgumentException("all records must belong to marketSymbol");
        }
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
    }
}
