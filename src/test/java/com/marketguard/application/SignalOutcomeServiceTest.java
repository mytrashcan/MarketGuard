package com.marketguard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketguard.config.MaintenanceProperties;
import com.marketguard.config.OutcomeProperties;
import com.marketguard.detection.evaluation.OutcomeStatus;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.domain.marketdata.SnapshotPrice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignalOutcomeServiceTest {

    private static final Instant SIGNAL_AT = Instant.parse("2026-07-31T01:00:00Z");
    private static final Instant NOW = SIGNAL_AT.plus(Duration.ofMinutes(40));
    private static final OutcomeProperties OUTCOME =
            new OutcomeProperties(Duration.ofMinutes(30), Duration.ofMinutes(3), Duration.ofMinutes(1), 3);
    private static final MaintenanceProperties MAINTENANCE =
            new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofHours(1));

    private final AnomalyRepository anomalies = mock(AnomalyRepository.class);
    private final PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
    private final SignalOutcomeService service = new SignalOutcomeService(
            anomalies, snapshots, OUTCOME, MAINTENANCE, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void recordsThePriceChangeBetweenTheSignalAndTheHorizon() {
        AnomalyRecord record = new AnomalyRecord("005930", RuleType.PRICE_SPIKE, Severity.WARNING, "급변동", SIGNAL_AT);
        when(anomalies.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(
                NOW.minus(Duration.ofMinutes(33)))).thenReturn(List.of(record));
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtDesc(
                "005930", SIGNAL_AT.minus(Duration.ofMinutes(3)), SIGNAL_AT))
                .thenReturn(Optional.of(new PriceSnapshot("005930", new BigDecimal("70000"), SIGNAL_AT)));
        Instant target = SIGNAL_AT.plus(Duration.ofMinutes(30));
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtAsc(
                "005930", target, target.plus(Duration.ofMinutes(3))))
                .thenReturn(Optional.of(new PriceSnapshot("005930", new BigDecimal("71400"), target)));

        assertThat(service.measurePending()).isEqualTo(1);

        assertThat(record.getOutcomeStatus()).isEqualTo(OutcomeStatus.MEASURED);
        assertThat(record.getOutcomeChangePercent()).isEqualByComparingTo("2");
        assertThat(record.getOutcomeMeasuredAt()).isEqualTo(NOW);
        assertThat(record.getOutcomeHorizonMinutes()).isEqualTo(30);
    }

    @Test
    void subtractsTheMedianMoveOfScannedStocksOverTheSameWindow() {
        AnomalyRecord record = new AnomalyRecord("005930", RuleType.PRICE_SPIKE, Severity.WARNING, "급변동", SIGNAL_AT);
        Instant target = SIGNAL_AT.plus(Duration.ofMinutes(30));
        when(anomalies.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(any()))
                .thenReturn(List.of(record));
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtDesc(eq("005930"), any(), any()))
                .thenReturn(Optional.of(new PriceSnapshot("005930", new BigDecimal("100"), SIGNAL_AT)));
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtAsc(eq("005930"), any(), any()))
                .thenReturn(Optional.of(new PriceSnapshot("005930", new BigDecimal("103"), target)));
        // 시장: 세 종목이 각각 +1%, +2%, +3% → 중앙값 +2%. 종목별로 시작 직전·끝 직후 가격을 쓴다.
        when(snapshots.findPricesBetween(SIGNAL_AT.minus(Duration.ofMinutes(3)), SIGNAL_AT)).thenReturn(List.of(
                new SnapshotPrice("A", new BigDecimal("90"), SIGNAL_AT.minusSeconds(60)),
                new SnapshotPrice("A", new BigDecimal("100"), SIGNAL_AT.minusSeconds(10)),
                new SnapshotPrice("B", new BigDecimal("100"), SIGNAL_AT),
                new SnapshotPrice("C", new BigDecimal("100"), SIGNAL_AT)));
        when(snapshots.findPricesBetween(target, target.plus(Duration.ofMinutes(3)))).thenReturn(List.of(
                new SnapshotPrice("A", new BigDecimal("101"), target.plusSeconds(10)),
                new SnapshotPrice("A", new BigDecimal("150"), target.plusSeconds(60)),
                new SnapshotPrice("B", new BigDecimal("102"), target),
                new SnapshotPrice("C", new BigDecimal("103"), target)));

        service.measurePending();

        assertThat(record.getOutcomeChangePercent()).isEqualByComparingTo("3");
        assertThat(record.getOutcomeMarketChangePercent()).isEqualByComparingTo("2");
        assertThat(record.getOutcomeExcessChangePercent()).isEqualByComparingTo("1");
    }

    @Test
    void marksMissingSnapshotsAsNoPriceData() {
        AnomalyRecord record = new AnomalyRecord("005930", RuleType.VOLUME_SURGE, Severity.WARNING, "거래량", SIGNAL_AT);
        when(anomalies.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(any()))
                .thenReturn(List.of(record));
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtDesc(eq("005930"), any(), any()))
                .thenReturn(Optional.empty());
        when(snapshots.findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtAsc(eq("005930"), any(), any()))
                .thenReturn(Optional.empty());

        service.measurePending();

        assertThat(record.getOutcomeStatus()).isEqualTo(OutcomeStatus.NO_PRICE_DATA);
        assertThat(record.getOutcomeChangePercent()).isNull();
    }

    @Test
    void doesNotMeasureMarketWideSignalsWithStockPrices() {
        AnomalyRecord record = new AnomalyRecord(
                "KOSPI", RuleType.INSTITUTIONAL_NET_BUY_SURGE, Severity.WARNING, "기관", SIGNAL_AT);
        when(anomalies.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(any()))
                .thenReturn(List.of(record));

        service.measurePending();

        assertThat(record.getOutcomeStatus()).isEqualTo(OutcomeStatus.NOT_APPLICABLE);
    }

    @Test
    void refusesToStartWhenSnapshotsExpireBeforeTheyCanBeMeasured() {
        MaintenanceProperties tooShort = new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofMinutes(30));

        assertThatThrownBy(() -> new SignalOutcomeService(
                anomalies, snapshots, OUTCOME, tooShort, Clock.fixed(NOW, ZoneOffset.UTC)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot-retention");
    }
}
