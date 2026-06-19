package com.marketguard.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 시세 보드용 현재가 응답 DTO.
 */
public record PriceView(String code, BigDecimal price, Instant capturedAt) {
}
