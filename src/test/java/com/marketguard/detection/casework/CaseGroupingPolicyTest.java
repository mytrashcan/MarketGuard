package com.marketguard.detection.casework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaseGroupingPolicyTest {

    private final CaseGroupingPolicy policy =
            new CaseGroupingPolicy(Duration.ofMinutes(10), Duration.ofMinutes(30));
    private final Instant base = Instant.parse("2026-07-15T01:00:00Z");

    @Test
    void mergesOnlyActiveCasesInsideTheGroupingWindow() {
        assertThat(policy.decide(new ExistingCase(CaseStatus.NEW, base, null), base.plusSeconds(600)))
                .isEqualTo(CaseGroupingDecision.MERGE);
        assertThat(policy.decide(new ExistingCase(CaseStatus.NEW, base, null), base.plusSeconds(601)))
                .isEqualTo(CaseGroupingDecision.CREATE);
    }

    @Test
    void reactivatesRecentlyClosedCasesAndCreatesAfterTheWindow() {
        ExistingCase closed = new ExistingCase(CaseStatus.CLOSED, base, base.plusSeconds(60));

        assertThat(policy.decide(closed, base.plus(Duration.ofMinutes(30))))
                .isEqualTo(CaseGroupingDecision.REACTIVATE);
        assertThat(policy.decide(closed, base.plus(Duration.ofMinutes(32))))
                .isEqualTo(CaseGroupingDecision.CREATE);
    }
}
