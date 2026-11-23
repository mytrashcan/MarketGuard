package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardChartMarkerTest {

    @Test
    void alignsSignalsToAnActualContainingBarAndOmitsOutOfRangeSignals() {
        String cases = DashboardSources.script("cases.js");
        String api = DashboardSources.script("api.js");

        assertThat(cases).contains(
                "function markerTimeForSignal(timestamp, barTimes)",
                "time >= observedAt && time - observedAt <= 60",
                "function caseChartBefore(detail)",
                ".filter(marker => marker !== null).sort((a,b) => a.time-b.time)",
                "조회된 120개 1분봉 범위 밖이라 차트에 표시하지 않았습니다.");
        assertThat(api).contains("&before=${encodeURIComponent(before)}");
        assertThat(cases).doesNotContain(
                "time:toBarTime(signal.marketObservedAt || signal.detectedAt,\"1m\")");
    }
}
