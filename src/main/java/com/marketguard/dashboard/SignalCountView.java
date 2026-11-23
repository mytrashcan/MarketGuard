package com.marketguard.dashboard;

import java.time.Instant;

/** since 이후 기록된 신호 수와 그중 심각 단계 신호 수. */
public record SignalCountView(Instant since, long signals, long critical) {
}
