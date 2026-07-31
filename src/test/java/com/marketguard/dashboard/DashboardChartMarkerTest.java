package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardChartMarkerTest {

    @Test
    void alignsSignalsToAnActualContainingBarAndOmitsOutOfRangeSignals() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));

        assertThat(html).contains(
                "function markerTimeForSignal(timestamp, barTimes)",
                "time >= observedAt && time - observedAt <= 60",
                "function caseChartBefore(detail)",
                "&before=${encodeURIComponent(before)}",
                ".filter(marker => marker !== null).sort((a,b) => a.time-b.time)",
                "조회된 120개 1분봉 범위 밖이라 차트에 표시하지 않았습니다.");
        assertThat(html).doesNotContain(
                "time:toBarTime(signal.marketObservedAt || signal.detectedAt,\"1m\")");
    }
}
