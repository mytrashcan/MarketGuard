package com.marketguard.detection.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 한 종목에 대한 탐지 시점의 입력 데이터.
 * current: 방금 수집한 스냅샷, recent: 비교용 직전 스냅샷들(최신순).
 * priceLimit/orderbook/candles/warnings: 해당 룰에서만 쓰는 부가 시장데이터(없으면 null/빈 목록).
 */
public record DetectionContext(
        MarketPrice current,
        List<MarketPrice> recent,
        PriceLimit priceLimit,
        OrderbookSnapshot orderbook,
        List<Candle> candles,
        List<Warning> warnings,
        Instant evaluationTime
) {
    public DetectionContext {
        Objects.requireNonNull(current, "current must not be null");
        recent = recent == null ? List.of() : List.copyOf(recent);
        candles = candles == null ? List.of() : List.copyOf(candles);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        Objects.requireNonNull(evaluationTime, "evaluationTime must not be null");
    }

    /** 가격 스냅샷만 필요한 룰/테스트용 간편 생성자. */
    public DetectionContext(MarketPrice current, List<MarketPrice> recent, Instant evaluationTime) {
        this(current, recent, null, null, List.of(), List.of(), evaluationTime);
    }

    /** 투자경고 외 부가데이터만 채우는 생성자(하위 호환). */
    public DetectionContext(MarketPrice current, List<MarketPrice> recent,
                            PriceLimit priceLimit, OrderbookSnapshot orderbook, List<Candle> candles,
                            Instant evaluationTime) {
        this(current, recent, priceLimit, orderbook, candles, List.of(), evaluationTime);
    }
}
