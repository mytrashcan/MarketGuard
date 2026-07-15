package com.marketguard.detection.casework;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 쿨다운과 독립적으로 신호를 사건에 묶거나 최근 종결 사건을 재활성화한다. */
public final class CaseGroupingPolicy {

    private final Duration groupingWindow;
    private final Duration reactivationWindow;

    public CaseGroupingPolicy(Duration groupingWindow, Duration reactivationWindow) {
        if (groupingWindow == null || groupingWindow.isZero() || groupingWindow.isNegative()) {
            throw new IllegalArgumentException("groupingWindow must be positive");
        }
        if (reactivationWindow == null || reactivationWindow.compareTo(groupingWindow) < 0) {
            throw new IllegalArgumentException("reactivationWindow must not be shorter than groupingWindow");
        }
        this.groupingWindow = groupingWindow;
        this.reactivationWindow = reactivationWindow;
    }

    public CaseGroupingDecision decide(ExistingCase existing, Instant signalAt) {
        Objects.requireNonNull(signalAt, "signalAt must not be null");
        if (existing == null || signalAt.isBefore(existing.lastDetectedAt())) {
            return CaseGroupingDecision.CREATE;
        }
        Duration gap = Duration.between(existing.lastDetectedAt(), signalAt);
        if (!existing.status().isTerminal()) {
            return gap.compareTo(groupingWindow) <= 0
                    ? CaseGroupingDecision.MERGE : CaseGroupingDecision.CREATE;
        }
        Instant reactivationBase = existing.closedAt() == null
                ? existing.lastDetectedAt() : existing.closedAt();
        Duration sinceClosed = Duration.between(reactivationBase, signalAt);
        return !sinceClosed.isNegative() && sinceClosed.compareTo(reactivationWindow) <= 0
                ? CaseGroupingDecision.REACTIVATE : CaseGroupingDecision.CREATE;
    }
}
