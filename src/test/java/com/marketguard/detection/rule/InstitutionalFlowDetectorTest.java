package com.marketguard.detection.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.InstitutionalFlowContext;
import com.marketguard.detection.model.InstitutionalTradingBreakdown;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.InvestorTradingAmount;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InstitutionalFlowDetectorTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-07-31T06:20:00Z");
    private final InstitutionalFlowDetector detector = new InstitutionalFlowDetector(
            decimal("3"), 5, 3, decimal("1000"), Duration.ofMinutes(30));

    @Test
    void detectsMarketWideInstitutionalNetBuying() {
        List<InstitutionalTradingRecord> records = history("KOSPI", "1500", "100", "200", "300");

        Anomaly anomaly = detector.evaluate(
                new InstitutionalFlowContext("KOSPI", records, EVALUATED_AT)).orElseThrow();

        assertThat(anomaly.stockCode()).isEqualTo("KOSPI");
        assertThat(anomaly.ruleType()).isEqualTo(RuleType.INSTITUTIONAL_NET_BUY_SURGE);
        assertThat(anomaly.severity()).isEqualTo(Severity.WARNING);
        assertThat(anomaly.evidence().direction()).isEqualTo(Direction.BUY);
        assertThat(anomaly.evidence().observedValue()).isEqualByComparingTo("1500");
        assertThat(anomaly.evidence().baselineValue()).isEqualByComparingTo("200");
        assertThat(anomaly.evidence().thresholdValue()).isEqualByComparingTo("1000");
        assertThat(anomaly.evidence().contextTags()).contains("당일 잠정치");
        assertThat(anomaly.evidence().measurements()).hasSize(10);
    }

    @Test
    void detectsMarketWideInstitutionalNetSelling() {
        List<InstitutionalTradingRecord> records = history("KOSDAQ", "-7000", "100", "-200", "300");

        Anomaly anomaly = detector.evaluate(
                new InstitutionalFlowContext("KOSDAQ", records, EVALUATED_AT)).orElseThrow();

        assertThat(anomaly.ruleType()).isEqualTo(RuleType.INSTITUTIONAL_NET_SELL_SURGE);
        assertThat(anomaly.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(anomaly.evidence().direction()).isEqualTo(Direction.SELL);
        assertThat(anomaly.evidence().observedValue()).isEqualByComparingTo("-7000");
        assertThat(anomaly.message()).contains("코스닥 시장", "순매도");
    }

    @Test
    void requiresBothTheRelativeAndAbsoluteThresholds() {
        assertThat(detector.evaluate(new InstitutionalFlowContext(
                "KOSPI", history("KOSPI", "999", "100", "200", "300"), EVALUATED_AT))).isEmpty();
        assertThat(detector.evaluate(new InstitutionalFlowContext(
                "KOSPI", history("KOSPI", "1000", "400", "500", "600"), EVALUATED_AT))).isEmpty();
    }

    @Test
    void skipsStaleOrHistoricalLatestRecords() {
        List<InstitutionalTradingRecord> stale = new ArrayList<>(
                history("KOSPI", "4000", "100", "200", "300"));
        InstitutionalTradingRecord latest = stale.get(0);
        stale.set(0, record("KOSPI", latest.date(), EVALUATED_AT.minus(Duration.ofHours(1)), "4000"));
        assertThat(detector.evaluate(new InstitutionalFlowContext("KOSPI", stale, EVALUATED_AT))).isEmpty();

        List<InstitutionalTradingRecord> historical = new ArrayList<>(stale);
        historical.set(0, record("KOSPI", LocalDate.of(2026, 7, 30),
                EVALUATED_AT.minus(Duration.ofMinutes(5)), "4000"));
        assertThat(detector.evaluate(
                new InstitutionalFlowContext("KOSPI", historical, EVALUATED_AT))).isEmpty();
    }

    @Test
    void rejectsBreakdownsThatDoNotMatchTheInstitutionTotal() {
        InvestorTradingAmount total = new InvestorTradingAmount(decimal("100"), decimal("50"));
        assertThatThrownBy(() -> new InstitutionalTradingRecord(
                "KOSPI", LocalDate.of(2026, 7, 31), EVALUATED_AT, total, breakdown("49")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seven-category breakdown");
    }

    private static List<InstitutionalTradingRecord> history(
            String market, String latestNet, String... previousNet) {
        List<InstitutionalTradingRecord> values = new ArrayList<>();
        values.add(record(market, LocalDate.of(2026, 7, 31),
                EVALUATED_AT.minus(Duration.ofMinutes(5)), latestNet));
        for (int index = 0; index < previousNet.length; index++) {
            values.add(record(market, LocalDate.of(2026, 7, 30).minusDays(index),
                    EVALUATED_AT.minus(Duration.ofDays(index + 1)), previousNet[index]));
        }
        return values;
    }

    private static InstitutionalTradingRecord record(
            String market, LocalDate date, Instant updatedAt, String netAmount) {
        BigDecimal net = decimal(netAmount);
        BigDecimal buy = net.signum() >= 0 ? net : BigDecimal.ZERO;
        BigDecimal sell = net.signum() < 0 ? net.abs() : BigDecimal.ZERO;
        return new InstitutionalTradingRecord(
                market, date, updatedAt, new InvestorTradingAmount(buy, sell), breakdown(netAmount));
    }

    private static InstitutionalTradingBreakdown breakdown(String netAmount) {
        InvestorTradingAmount first = amount(netAmount);
        InvestorTradingAmount zero = amount("0");
        return new InstitutionalTradingBreakdown(first, zero, zero, zero, zero, zero, zero);
    }

    private static InvestorTradingAmount amount(String netAmount) {
        BigDecimal value = decimal(netAmount);
        return value.signum() >= 0
                ? new InvestorTradingAmount(value, BigDecimal.ZERO)
                : new InvestorTradingAmount(BigDecimal.ZERO, value.abs());
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
