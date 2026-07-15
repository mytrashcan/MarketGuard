package com.marketguard.detection.casework;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CaseTransitionPolicyTest {

    private final CaseTransitionPolicy policy = new CaseTransitionPolicy();

    @Test
    void requiresAReasonForTerminalStates() {
        assertThatThrownBy(() -> policy.validate(CaseStatus.REVIEWING, CaseStatus.CLOSED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("closure reason");
        assertThatCode(() -> policy.validate(
                CaseStatus.REVIEWING, CaseStatus.CLOSED, ClosureReason.MARKET_WIDE_MOVE))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsExplicitReopeningButRejectsNoOpTransitions() {
        assertThatCode(() -> policy.validate(CaseStatus.CLOSED, CaseStatus.WATCHING, null))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(CaseStatus.NEW, CaseStatus.NEW, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
