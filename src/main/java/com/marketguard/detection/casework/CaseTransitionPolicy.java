package com.marketguard.detection.casework;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;

/** 허용 상태 전이와 종결 사유 요구사항을 한곳에서 강제한다. */
public final class CaseTransitionPolicy {

    private static final Map<CaseStatus, EnumSet<CaseStatus>> ALLOWED = allowedTransitions();

    public void validate(CaseStatus current, CaseStatus target, ClosureReason reason) {
        Objects.requireNonNull(current, "current status must not be null");
        Objects.requireNonNull(target, "target status must not be null");
        if (current == target) {
            throw new IllegalArgumentException("case is already in status " + target);
        }
        if (!ALLOWED.get(current).contains(target)) {
            throw new IllegalArgumentException("transition from " + current + " to " + target + " is not allowed");
        }
        if (target.isTerminal() && reason == null) {
            throw new IllegalArgumentException("closure reason is required for terminal status");
        }
        if (!target.isTerminal() && reason != null) {
            throw new IllegalArgumentException("closure reason is only allowed for terminal status");
        }
    }

    private static Map<CaseStatus, EnumSet<CaseStatus>> allowedTransitions() {
        Map<CaseStatus, EnumSet<CaseStatus>> transitions = new EnumMap<>(CaseStatus.class);
        transitions.put(CaseStatus.NEW, EnumSet.of(CaseStatus.REVIEWING, CaseStatus.WATCHING,
                CaseStatus.DISMISSED, CaseStatus.ESCALATED, CaseStatus.CLOSED));
        transitions.put(CaseStatus.REVIEWING, EnumSet.of(CaseStatus.WATCHING, CaseStatus.DISMISSED,
                CaseStatus.ESCALATED, CaseStatus.CLOSED));
        transitions.put(CaseStatus.WATCHING, EnumSet.of(CaseStatus.REVIEWING, CaseStatus.DISMISSED,
                CaseStatus.ESCALATED, CaseStatus.CLOSED));
        transitions.put(CaseStatus.ESCALATED, EnumSet.of(CaseStatus.REVIEWING, CaseStatus.WATCHING,
                CaseStatus.DISMISSED, CaseStatus.CLOSED));
        transitions.put(CaseStatus.DISMISSED,
                EnumSet.of(CaseStatus.REVIEWING, CaseStatus.WATCHING, CaseStatus.CLOSED));
        transitions.put(CaseStatus.CLOSED,
                EnumSet.of(CaseStatus.REVIEWING, CaseStatus.WATCHING));
        return Map.copyOf(transitions);
    }
}
