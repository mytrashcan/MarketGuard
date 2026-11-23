package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.CaseTransitionPolicy;
import com.marketguard.detection.casework.ClosureReason;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 대시보드가 보여주는 상태 전이 표가 서버 정책과 어긋나지 않는지 확인한다. */
class DashboardTransitionPolicyTest {

    private static final Pattern TABLE = Pattern.compile("export const TRANSITIONS = \\{(.*?)\\};", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("(\\w+):\\s*\\[([^\\]]*)\\]");
    private static final Pattern TERMINAL = Pattern.compile("export const TERMINAL_STATUSES = new Set\\(\\[([^\\]]*)\\]\\)");

    @Test
    void offersExactlyTheTransitionsTheServerAllows() {
        Map<CaseStatus, Set<CaseStatus>> dashboard = dashboardTransitions();
        CaseTransitionPolicy policy = new CaseTransitionPolicy();

        assertThat(dashboard.keySet()).containsExactlyInAnyOrder(CaseStatus.values());
        for (CaseStatus from : CaseStatus.values()) {
            for (CaseStatus to : CaseStatus.values()) {
                boolean allowedByServer = allowed(policy, from, to);
                assertThat(dashboard.get(from).contains(to))
                        .as("dashboard offers %s -> %s", from, to)
                        .isEqualTo(allowedByServer);
            }
        }
    }

    @Test
    void asksForAClosureReasonOnExactlyTheServerTerminalStatuses() {
        Matcher matcher = TERMINAL.matcher(DashboardSources.script("format.js"));
        assertThat(matcher.find()).as("TERMINAL_STATUSES in format.js").isTrue();

        Set<CaseStatus> dashboardTerminal = statuses(matcher.group(1));
        Set<CaseStatus> serverTerminal = Arrays.stream(CaseStatus.values())
                .filter(CaseStatus::isTerminal)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CaseStatus.class)));
        assertThat(dashboardTerminal).isEqualTo(serverTerminal);
    }

    private static boolean allowed(CaseTransitionPolicy policy, CaseStatus from, CaseStatus to) {
        try {
            policy.validate(from, to, to.isTerminal() ? ClosureReason.OTHER : null);
            return true;
        } catch (IllegalArgumentException rejected) {
            return false;
        }
    }

    private static Map<CaseStatus, Set<CaseStatus>> dashboardTransitions() {
        Matcher table = TABLE.matcher(DashboardSources.script("format.js"));
        assertThat(table.find()).as("TRANSITIONS in format.js").isTrue();
        Map<CaseStatus, Set<CaseStatus>> transitions = new EnumMap<>(CaseStatus.class);
        Matcher row = ROW.matcher(table.group(1));
        while (row.find()) {
            transitions.put(CaseStatus.valueOf(row.group(1)), statuses(row.group(2)));
        }
        return transitions;
    }

    private static Set<CaseStatus> statuses(String quotedList) {
        return Arrays.stream(quotedList.split(","))
                .map(value -> value.strip().replace("\"", ""))
                .filter(value -> !value.isEmpty())
                .map(CaseStatus::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CaseStatus.class)));
    }
}
