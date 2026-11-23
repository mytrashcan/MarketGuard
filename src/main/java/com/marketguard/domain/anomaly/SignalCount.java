package com.marketguard.domain.anomaly;

/** 같은 행 집합에서 센 전체 신호 수와 그중 심각 신호 수. */
public record SignalCount(long signals, long critical) {
}
