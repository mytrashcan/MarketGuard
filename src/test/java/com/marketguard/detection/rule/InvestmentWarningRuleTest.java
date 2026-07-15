package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.model.Warning;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvestmentWarningRuleTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-15T00:00:00Z");
    private static final LocalDate TODAY = EVALUATED_AT.atZone(ZoneId.of("Asia/Seoul")).toLocalDate();

    private final InvestmentWarningRule rule = new InvestmentWarningRule();

    private DetectionContext ctx(List<Warning> warnings) {
        MarketPrice current = new MarketPrice("900110", new BigDecimal("1000"), EVALUATED_AT);
        return new DetectionContext(current, List.of(), null, null, List.of(), warnings, EVALUATED_AT);
    }

    @Test
    @DisplayName("유효한 투자위험 지정은 CRITICAL로 탐지한다")
    void detectsRiskAsCritical() {
        DetectionContext context = ctx(List.of(new Warning("INVESTMENT_RISK", TODAY.minusDays(1), null)));
        assertThat(rule.evaluate(context)).get().extracting(Anomaly::severity).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("여러 지정 중 가장 심각한 것을 택한다")
    void picksMostSevere() {
        DetectionContext context = ctx(List.of(
                new Warning("OVERHEATED", TODAY.minusDays(1), null),
                new Warning("INVESTMENT_RISK", TODAY.minusDays(1), TODAY.plusDays(2))));
        assertThat(rule.evaluate(context)).get().extracting(Anomaly::ruleType).isEqualTo(RuleType.INVESTMENT_WARNING);
        assertThat(rule.evaluate(context)).get().extracting(Anomaly::severity).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("종료일이 지난 지정은 무시한다")
    void ignoresExpired() {
        DetectionContext context = ctx(List.of(new Warning("INVESTMENT_WARNING", TODAY.minusDays(10), TODAY.minusDays(1))));
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    @DisplayName("VI 등 비대상 항목은 무시한다")
    void ignoresNonTarget() {
        DetectionContext context = ctx(List.of(new Warning("VI_STATIC", TODAY, null)));
        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    @DisplayName("지정이 없으면 탐지하지 않는다")
    void ignoresEmpty() {
        assertThat(rule.evaluate(ctx(List.of()))).isEmpty();
    }
}
