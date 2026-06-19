package com.marketguard.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 시세 보드용 현재가 응답 DTO.
 * changePercent: 당일 시가(open) 대비 등락률(%). 시가를 알 수 없으면 null.
 */
public record PriceView(
        String code,
        BigDecimal price,
        BigDecimal open,
        Double changePercent,
        Instant capturedAt
) {
    public static PriceView of(String code, BigDecimal price, BigDecimal open, Instant capturedAt) {
        Double changePercent = null;
        if (price != null && open != null && open.signum() != 0) {
            changePercent = price.subtract(open).doubleValue() / open.doubleValue() * 100.0;
        }
        return new PriceView(code, price, open, changePercent, capturedAt);
    }
}
