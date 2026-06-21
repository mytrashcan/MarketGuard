package com.marketguard.detection.model;

import com.marketguard.domain.marketdata.PriceSnapshot;
import java.util.List;

/**
 * 한 종목에 대한 탐지 시점의 입력 데이터.
 * current: 방금 수집한 스냅샷, recent: 비교용 직전 스냅샷들(최신순).
 * priceLimit/orderbook/candles/warnings: 해당 룰에서만 쓰는 부가 시장데이터(없으면 null/빈 목록).
 */
public record DetectionContext(
        PriceSnapshot current,
        List<PriceSnapshot> recent,
        PriceLimit priceLimit,
        OrderbookSnapshot orderbook,
        List<Candle> candles,
        List<Warning> warnings
) {
    /** 가격 스냅샷만 필요한 룰/테스트용 간편 생성자. */
    public DetectionContext(PriceSnapshot current, List<PriceSnapshot> recent) {
        this(current, recent, null, null, List.of(), List.of());
    }

    /** 투자경고 외 부가데이터만 채우는 생성자(하위 호환). */
    public DetectionContext(PriceSnapshot current, List<PriceSnapshot> recent,
                            PriceLimit priceLimit, OrderbookSnapshot orderbook, List<Candle> candles) {
        this(current, recent, priceLimit, orderbook, candles, List.of());
    }
}
