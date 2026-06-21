package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.model.Warning;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 거래소 지정종목 탐지: 투자경고/투자위험/단기과열/정리매매 등 현재 유효한 지정이 있으면 알린다.
 * VI(변동성완화장치)·신주인수권 등 단기·비지정성 항목은 노이즈라 제외한다.
 */
@Component
public class InvestmentWarningRule implements DetectionRule {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Map<String, Severity> SEVERITY = Map.of(
            "INVESTMENT_RISK", Severity.CRITICAL,
            "LIQUIDATION_TRADING", Severity.CRITICAL,
            "INVESTMENT_WARNING", Severity.WARNING,
            "OVERHEATED", Severity.WARNING);

    private static final Map<String, String> LABEL = Map.of(
            "INVESTMENT_RISK", "투자위험",
            "LIQUIDATION_TRADING", "정리매매",
            "INVESTMENT_WARNING", "투자경고",
            "OVERHEATED", "단기과열");

    @Override
    public RuleType type() {
        return RuleType.INVESTMENT_WARNING;
    }

    @Override
    public Optional<Anomaly> evaluate(DetectionContext context) {
        List<Warning> warnings = context.warnings();
        if (warnings == null || warnings.isEmpty()) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(KST);

        Warning top = null;
        Severity topSeverity = null;
        for (Warning warning : warnings) {
            Severity severity = SEVERITY.get(warning.type());
            if (severity == null || !isActive(warning, today)) {
                continue;   // 비지정성(VI 등)이거나 현재 유효하지 않은 지정
            }
            if (topSeverity == null || severity.ordinal() > topSeverity.ordinal()) {
                top = warning;
                topSeverity = severity;
            }
        }
        if (top == null) {
            return Optional.empty();
        }
        String label = LABEL.getOrDefault(top.type(), top.type());
        String period = top.startDate() + " ~ " + (top.endDate() == null ? "진행중" : top.endDate().toString());
        String message = "거래소 지정종목: %s (%s)".formatted(label, period);
        return Optional.of(Anomaly.of(
                context.current().getStockCode(), RuleType.INVESTMENT_WARNING, topSeverity, message));
    }

    private boolean isActive(Warning warning, LocalDate today) {
        if (warning.startDate() != null && warning.startDate().isAfter(today)) {
            return false;
        }
        return warning.endDate() == null || !warning.endDate().isBefore(today);
    }
}
