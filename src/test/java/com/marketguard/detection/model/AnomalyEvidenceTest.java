package com.marketguard.detection.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnomalyEvidenceTest {

    @Test
    void copiesAndDeduplicatesUserVisibleLists() {
        AnomalyEvidence evidence = AnomalyEvidence.builder("제목", "요약", "설명")
                .contextTags(List.of("장중", "장중"))
                .recommendedChecks(List.of("확인"))
                .build();

        assertThat(evidence.contextTags()).containsExactly("장중");
        assertThat(evidence.caution()).contains("판단할 수 없습니다");
    }

    @Test
    void legacySignalsReceiveAnExplicitDataLimitation() {
        Anomaly anomaly = new Anomaly("005930", RuleType.PRICE_SPIKE, Severity.WARNING,
                "기존 메시지", Instant.parse("2026-07-15T01:00:00Z"));

        assertThat(anomaly.evidence().explanation()).contains("원시 비교값이 남아 있지 않습니다");
    }

    @Test
    void rejectsUnboundedContextData() {
        assertThatThrownBy(() -> AnomalyEvidence.builder("제목", "요약", "설명")
                .contextTags(java.util.Collections.nCopies(21, "태그"))
                .build()).isInstanceOf(IllegalArgumentException.class);
    }
}
