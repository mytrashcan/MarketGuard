package com.marketguard.domain.marketdata;

import java.math.BigDecimal;
import java.time.Instant;

/** 시장 기준 계산에 쓰는 스냅샷 가격 조회 결과. */
public record SnapshotPrice(String stockCode, BigDecimal price, Instant capturedAt) {
}
