package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutcomePropertiesTest {

    private static OutcomeProperties withOffsets(List<Duration> offsets) {
        return new OutcomeProperties(Duration.ofMinutes(30), Duration.ofMinutes(3), Duration.ofMinutes(1), 30, "", 5,
                offsets);
    }

    @Test
    void sortsAndDeduplicatesPathOffsets() {
        assertThat(withOffsets(List.of(Duration.ofMinutes(60), Duration.ofMinutes(5), Duration.ofMinutes(5)))
                .pathOffsets()).containsExactly(Duration.ofMinutes(5), Duration.ofMinutes(60));
    }

    @Test
    void retentionMustCoverTheLatestPathPoint() {
        // 60분 + 확인 구간 3분 × 2 + 측정 주기 1분
        assertThat(withOffsets(List.of(Duration.ofMinutes(60))).requiredSnapshotRetention())
                .isEqualTo(Duration.ofMinutes(67));
        // 경로 시점이 horizon보다 이르면 horizon이 기준
        assertThat(withOffsets(List.of(Duration.ofMinutes(5))).requiredSnapshotRetention())
                .isEqualTo(Duration.ofMinutes(37));
    }

    @Test
    void rejectsOffsetsThatAreNotWholeMinutes() {
        assertThatThrownBy(() -> withOffsets(List.of(Duration.ofSeconds(90))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withOffsets(List.of(Duration.ofHours(7))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
