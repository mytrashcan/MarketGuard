package com.marketguard.detection.rule;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.EvidenceMeasurement;
import com.marketguard.detection.model.InstitutionalFlowContext;
import com.marketguard.detection.model.InstitutionalTradingBreakdown;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.MarketInstrument;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Detects unusually large market-wide institutional net buying or selling.
 *
 * <p>The baseline is the median absolute daily net amount. A signal must cross both the
 * baseline multiplier and an absolute KRW floor, preventing tiny baselines from creating noise.
 */
public final class InstitutionalFlowDetector {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BigDecimal multiplier;
    private final int lookback;
    private final int minimumSamples;
    private final BigDecimal minimumNetAmount;
    private final Duration maxAge;

    public InstitutionalFlowDetector(
            BigDecimal multiplier,
            int lookback,
            int minimumSamples,
            BigDecimal minimumNetAmount,
            Duration maxAge) {
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) <= 0) {
            throw new IllegalArgumentException("multiplier must be greater than one");
        }
        if (lookback < 2 || lookback > 99) {
            throw new IllegalArgumentException("lookback must be between 2 and 99");
        }
        if (minimumSamples < 2 || minimumSamples > lookback) {
            throw new IllegalArgumentException("minimumSamples must be between 2 and lookback");
        }
        if (minimumNetAmount == null || minimumNetAmount.signum() <= 0) {
            throw new IllegalArgumentException("minimumNetAmount must be positive");
        }
        if (maxAge == null || maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
        this.multiplier = multiplier;
        this.lookback = lookback;
        this.minimumSamples = minimumSamples;
        this.minimumNetAmount = minimumNetAmount;
        this.maxAge = maxAge;
    }

    public Optional<Anomaly> evaluate(InstitutionalFlowContext context) {
        List<InstitutionalTradingRecord> sorted = context.records().stream()
                .sorted(Comparator.comparing(InstitutionalTradingRecord::date).reversed())
                .toList();
        if (sorted.size() < minimumSamples + 1) {
            return Optional.empty();
        }

        InstitutionalTradingRecord latest = sorted.get(0);
        LocalDate evaluationDate = LocalDate.ofInstant(context.evaluationTime(), KST);
        if (!latest.date().equals(evaluationDate)
                || latest.updatedAt().isBefore(context.evaluationTime().minus(maxAge))
                || latest.updatedAt().isAfter(context.evaluationTime().plusSeconds(60))) {
            return Optional.empty();
        }

        List<BigDecimal> previousAbsoluteNet = sorted.stream()
                .skip(1)
                .limit(lookback)
                .map(InstitutionalTradingRecord::netAmount)
                .map(BigDecimal::abs)
                .sorted()
                .toList();
        if (previousAbsoluteNet.size() < minimumSamples) {
            return Optional.empty();
        }

        BigDecimal baseline = median(previousAbsoluteNet);
        if (baseline.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal threshold = baseline.multiply(multiplier).max(minimumNetAmount);
        BigDecimal net = latest.netAmount();
        BigDecimal absoluteNet = net.abs();
        if (absoluteNet.compareTo(threshold) < 0 || net.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal ratio = absoluteNet.divide(
                baseline, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
        Direction direction = net.signum() > 0 ? Direction.BUY : Direction.SELL;
        RuleType ruleType = direction == Direction.BUY
                ? RuleType.INSTITUTIONAL_NET_BUY_SURGE
                : RuleType.INSTITUTIONAL_NET_SELL_SURGE;
        Severity severity = absoluteNet.compareTo(threshold.multiply(BigDecimal.valueOf(2))) >= 0
                ? Severity.CRITICAL : Severity.WARNING;
        String directionLabel = direction == Direction.BUY ? "순매수" : "순매도";
        String marketName = MarketInstrument.displayName(context.marketSymbol());
        String ratioText = ratio.setScale(1, DecimalMath.ROUNDING_MODE).toPlainString();
        String summary = "%s 기관 합계가 %s %s원으로 최근 기준의 %s배입니다."
                .formatted(marketName, directionLabel, absoluteNet.toPlainString(), ratioText);

        AnomalyEvidence evidence = AnomalyEvidence.builder(
                        "기관 " + directionLabel + " 급증",
                        summary,
                        "토스증권이 제공한 KRX 시장 전체 기관 매매대금에서 계산한 신호입니다. "
                                + "개별 종목이나 특정 기관의 매매로 해석할 수 없습니다.")
                .values(net, baseline, threshold, ratio, "원")
                .lookback("직전 " + previousAbsoluteNet.size() + "거래일 기관 순매수 절대값 중앙값")
                .direction(direction)
                .contextTags(List.of(marketName, "기관 수급", "당일 잠정치"))
                .recommendedChecks(List.of(
                        "장 종료 후 확정된 기관 매매대금으로 다시 확인하세요.",
                        "같은 기간의 시장지수와 외국인·개인 수급 방향을 함께 확인하세요.",
                        "이 신호를 개별 종목 또는 특정 기관의 매매로 해석하지 마세요."))
                .marketObservedAt(latest.updatedAt())
                .caution("당일 수치는 장 종료 전까지 갱신될 수 있는 잠정치이며, "
                        + "시장 전체 합계이므로 개별 종목이나 특정 기관의 거래를 나타내지 않습니다.")
                .measurements(measurements(latest, baseline, threshold, ratio))
                .build();
        String message = "%s 기관 %s 급증: 순액 %s원 (최근 중앙값의 %s배)"
                .formatted(marketName, directionLabel, net.toPlainString(), ratioText);
        return Optional.of(Anomaly.explained(
                context.marketSymbol(), ruleType, severity, message, context.evaluationTime(), evidence));
    }

    private static BigDecimal median(List<BigDecimal> sortedValues) {
        int size = sortedValues.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sortedValues.get(middle);
        }
        return sortedValues.get(middle - 1).add(sortedValues.get(middle))
                .divide(BigDecimal.valueOf(2), DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
    }

    private static List<EvidenceMeasurement> measurements(
            InstitutionalTradingRecord record,
            BigDecimal baseline,
            BigDecimal threshold,
            BigDecimal ratio) {
        InstitutionalTradingBreakdown value = record.breakdown();
        return List.of(
                new EvidenceMeasurement("institution_net", "기관 합계 순매수",
                        record.netAmount(), baseline, threshold, ratio, "원"),
                new EvidenceMeasurement("institution_buy", "기관 합계 매수",
                        record.institution().buyAmount(), null, null, null, "원"),
                new EvidenceMeasurement("institution_sell", "기관 합계 매도",
                        record.institution().sellAmount(), null, null, null, "원"),
                netMeasurement("financial_investment_net", "금융투자 순매수",
                        value.financialInvestment().netAmount()),
                netMeasurement("insurance_net", "보험 순매수", value.insurance().netAmount()),
                netMeasurement("trust_net", "투신 순매수", value.trust().netAmount()),
                netMeasurement("private_equity_fund_net", "사모펀드 순매수",
                        value.privateEquityFund().netAmount()),
                netMeasurement("bank_net", "은행 순매수", value.bank().netAmount()),
                netMeasurement("other_financial_net", "기타금융 순매수",
                        value.otherFinancialInstitution().netAmount()),
                netMeasurement("pension_fund_net", "연기금 순매수", value.pensionFund().netAmount()));
    }

    private static EvidenceMeasurement netMeasurement(String key, String label, BigDecimal value) {
        return new EvidenceMeasurement(key, label, value, null, null, null, "원");
    }
}
