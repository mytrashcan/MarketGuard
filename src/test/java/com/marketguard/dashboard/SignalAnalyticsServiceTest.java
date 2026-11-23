package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.config.OutcomeProperties;
import com.marketguard.detection.evaluation.OutcomeStatus;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.anomaly.SignalStatRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalAnalyticsServiceTest {

    // 2026-07-31 10:00 KST
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");
    private static final OutcomeProperties OUTCOME =
            new OutcomeProperties(Duration.ofMinutes(30), Duration.ofMinutes(3), Duration.ofMinutes(1), 3);

    private final AnomalyRepository repository = mock(AnomalyRepository.class);
    private final SignalAnalyticsService service =
            new SignalAnalyticsService(repository, OUTCOME, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void groupsSignalsByKoreanTradingDayAndFillsQuietDays() {
        Instant startOfRange = Instant.parse("2026-07-28T15:00:00Z"); // 2026-07-29 00:00 KST
        when(repository.findStatRows(startOfRange, NOW)).thenReturn(List.of(
                // 2026-07-29 23:30 UTC = 2026-07-30 08:30 KST
                row(RuleType.PRICE_SPIKE, Severity.CRITICAL, "005930", "2026-07-29T23:30:00Z", null, null),
                row(RuleType.PRICE_SPIKE, Severity.WARNING, "005930", "2026-07-30T01:00:00Z", null, null),
                row(RuleType.VOLUME_SURGE, Severity.WARNING, "000660", "2026-07-31T00:30:00Z", null, null)));

        SignalAnalyticsView view = service.summarize(3);

        verify(repository).findStatRows(startOfRange, NOW);
        assertThat(view.from()).isEqualTo(LocalDate.of(2026, 7, 29));
        assertThat(view.to()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(view.daily()).extracting(SignalAnalyticsView.Day::signals).containsExactly(0L, 2L, 1L);
        assertThat(view.daily().get(1).critical()).isEqualTo(1);
        assertThat(view.daily().get(1).stocks()).isEqualTo(1);
        assertThat(view.horizonMinutes()).isEqualTo(30);
    }

    @Test
    void reportsPerRuleOutcomesAndMeasurementCoverage() {
        when(repository.findStatRows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(
                        row(RuleType.PRICE_SPIKE, Severity.WARNING, "005930", "2026-07-31T00:00:00Z",
                                OutcomeStatus.MEASURED, measured("1.5", true)),
                        row(RuleType.PRICE_SPIKE, Severity.WARNING, "005930", "2026-07-31T00:10:00Z",
                                OutcomeStatus.MEASURED, measured("-0.5", false)),
                        row(RuleType.PRICE_SPIKE, Severity.WARNING, "005930", "2026-07-31T00:20:00Z",
                                OutcomeStatus.NO_PRICE_DATA, null),
                        row(RuleType.INSTITUTIONAL_NET_BUY_SURGE, Severity.WARNING, "KOSPI", "2026-07-31T00:30:00Z",
                                OutcomeStatus.NOT_APPLICABLE, null),
                        row(RuleType.VOLUME_SURGE, Severity.WARNING, "000660", "2026-07-31T00:50:00Z", null, null)));

        SignalAnalyticsView view = service.summarize(1);

        SignalAnalyticsView.Rule spike = view.rules().get(0);
        assertThat(spike.ruleType()).isEqualTo(RuleType.PRICE_SPIKE);
        assertThat(spike.signals()).isEqualTo(3);
        assertThat(spike.measured()).isEqualTo(2);
        assertThat(spike.followed().ratePercent()).isEqualByComparingTo("50");
        assertThat(spike.medianAbsChangePercent()).isEqualByComparingTo("1.00");
        // +1.5%(상승 신호, 지속) - 시장 0.5% = +1.0% → 여전히 지속, -0.5%(반대) - 0.5% = -1.0% → 반대
        assertThat(spike.followedAfterMarket().samples()).isEqualTo(2);
        assertThat(spike.followedAfterMarket().ratePercent()).isEqualByComparingTo("50");
        assertThat(view.coverage()).isEqualTo(new SignalAnalyticsView.Coverage(2, 1, 1, 1, 0));
    }

    @Test
    void keepsResultsMeasuredWithAnotherHorizonOutOfTheRuleStatistics() {
        SignalStatRow current = row(RuleType.PRICE_SPIKE, Severity.WARNING, "005930", "2026-07-31T00:00:00Z",
                OutcomeStatus.MEASURED, measured("1.5", true));
        SignalStatRow oldHorizon = new SignalStatRow(RuleType.PRICE_SPIKE, Severity.WARNING, "005930",
                Instant.parse("2026-07-31T00:10:00Z"), OutcomeStatus.MEASURED, new BigDecimal("100"),
                new BigDecimal("98"), new BigDecimal("-2"), false, null, null, null, 60);
        when(repository.findStatRows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(current, oldHorizon));

        SignalAnalyticsView view = service.summarize(1);

        SignalAnalyticsView.Rule spike = view.rules().get(0);
        assertThat(spike.signals()).isEqualTo(2);
        assertThat(spike.measured()).isEqualTo(1);
        assertThat(spike.followed().ratePercent()).isEqualByComparingTo("100");
        assertThat(view.coverage().otherHorizon()).isEqualTo(1);
        assertThat(view.coverage().measured()).isEqualTo(1);
    }

    private static Object[] measured(String change, boolean followed) {
        return new Object[] {new BigDecimal(change), followed};
    }

    private static SignalStatRow row(RuleType rule, Severity severity, String code, String at,
                                     OutcomeStatus status, Object[] measured) {
        BigDecimal change = measured == null ? null : (BigDecimal) measured[0];
        Boolean followed = measured == null ? null : (Boolean) measured[1];
        BigDecimal base = measured == null ? null : new BigDecimal("100");
        BigDecimal outcome = change == null ? null : base.add(change);
        BigDecimal market = change == null ? null : new BigDecimal("0.5");
        BigDecimal excess = change == null ? null : change.subtract(market);
        Boolean followedAfterMarket = excess == null || excess.signum() == 0 ? null
                : (excess.signum() > 0) == (followed == change.signum() > 0);
        return new SignalStatRow(rule, severity, code, Instant.parse(at), status, base, outcome, change, followed,
                market, excess, followedAfterMarket, status == null ? null : 30);
    }
}
