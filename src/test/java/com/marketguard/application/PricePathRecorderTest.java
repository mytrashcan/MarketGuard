package com.marketguard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.application.port.SectorLookup;
import com.marketguard.config.MaintenanceProperties;
import com.marketguard.config.OutcomeProperties;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.anomaly.SignalPricePoint;
import com.marketguard.domain.anomaly.SignalPricePointRepository;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.domain.marketdata.SnapshotPrice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class PricePathRecorderTest {

    private static final Instant SIGNAL_AT = Instant.parse("2026-07-31T01:00:00Z");
    private static final Instant NOW = SIGNAL_AT.plus(Duration.ofMinutes(20));
    private static final Duration WINDOW = Duration.ofMinutes(3);
    private static final OutcomeProperties OUTCOME = new OutcomeProperties(Duration.ofMinutes(30), WINDOW,
            Duration.ofMinutes(1), 2, "", 1, List.of(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(60)));
    private static final MaintenanceProperties MAINTENANCE =
            new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofMinutes(90));
    private static final Map<String, String> SECTOR_MAP = Map.of("005930", "반도체", "A", "반도체");
    private static final SectorLookup SECTORS = code -> Optional.ofNullable(SECTOR_MAP.get(code));

    private final AnomalyRepository anomalies = mock(AnomalyRepository.class);
    private final PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
    private final SignalPricePointRepository points = mock(SignalPricePointRepository.class);

    private PricePathRecorder recorder(OutcomeProperties outcome, MaintenanceProperties maintenance) {
        return new PricePathRecorder(anomalies, snapshots, points, outcome, maintenance, SECTORS,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void recordsRawPricesAndBenchmarkMovesForEveryDuePoint() {
        AnomalyRecord record = signal("005930");
        Instant earliest = NOW.minus(Duration.ofMinutes(90)).plus(WINDOW);
        when(anomalies.findMissingPricePoint(any(), any(), anyInt(), any())).thenReturn(List.of());
        when(anomalies.findMissingPricePoint(earliest, NOW.minus(Duration.ofMinutes(8)), 5, Limit.of(500)))
                .thenReturn(List.of(record));
        when(anomalies.findMissingPricePoint(earliest, NOW.minus(Duration.ofMinutes(18)), 15, Limit.of(500)))
                .thenReturn(List.of(record));
        Instant fiveAfter = SIGNAL_AT.plus(Duration.ofMinutes(5));
        Instant fifteenAfter = SIGNAL_AT.plus(Duration.ofMinutes(15));
        when(snapshots.findPricesBetween(SIGNAL_AT.minus(WINDOW), SIGNAL_AT.plus(Duration.ofMinutes(1))))
                .thenReturn(List.of(
                        new SnapshotPrice("005930", new BigDecimal("100"), SIGNAL_AT.minusSeconds(30)),
                        new SnapshotPrice("A", new BigDecimal("100"), SIGNAL_AT),
                        new SnapshotPrice("B", new BigDecimal("100"), SIGNAL_AT),
                        // 신호 뒤 가격은 기준 가격이 아니다
                        new SnapshotPrice("005930", new BigDecimal("999"), SIGNAL_AT.plusSeconds(20))));
        when(snapshots.findPricesBetween(fiveAfter, fiveAfter.plus(Duration.ofMinutes(4)))).thenReturn(List.of(
                new SnapshotPrice("005930", new BigDecimal("104"), fiveAfter.plusSeconds(10)),
                new SnapshotPrice("A", new BigDecimal("101"), fiveAfter),
                new SnapshotPrice("B", new BigDecimal("103"), fiveAfter)));
        // 15분 시점에는 신호 종목 가격이 없다
        when(snapshots.findPricesBetween(fifteenAfter, fifteenAfter.plus(Duration.ofMinutes(4)))).thenReturn(List.of(
                new SnapshotPrice("A", new BigDecimal("102"), fifteenAfter)));

        assertThat(recorder(OUTCOME, MAINTENANCE).recordDue()).isEqualTo(2);

        List<SignalPricePoint> saved = savedPoints();
        SignalPricePoint five = saved.get(0);
        assertThat(five.getAnomalyId()).isEqualTo(7L);
        assertThat(five.getOffsetMinutes()).isEqualTo(5);
        assertThat(five.getBasePrice()).isEqualByComparingTo("100");
        assertThat(five.getPrice()).isEqualByComparingTo("104");
        // 시장: +4%, +1%, +3% → +3%. 업종(신호 종목 제외): A +1%
        assertThat(five.getMarketChangePercent()).isEqualByComparingTo("3");
        assertThat(five.getSectorChangePercent()).isEqualByComparingTo("1");
        assertThat(five.getRecordedAt()).isEqualTo(NOW);

        SignalPricePoint fifteen = saved.get(1);
        assertThat(fifteen.getOffsetMinutes()).isEqualTo(15);
        assertThat(fifteen.getBasePrice()).isEqualByComparingTo("100");
        assertThat(fifteen.getPrice()).isNull();
        // 두 시점 모두 가격이 있는 종목이 A 하나뿐이라 시장 표본(2) 부족
        assertThat(fifteen.getMarketChangePercent()).isNull();
    }

    @Test
    void asksEachPointOnlyForSignalsWhoseWindowHasClosedAndWhoseSnapshotsRemain() {
        when(anomalies.findMissingPricePoint(any(), any(), anyInt(), any())).thenReturn(List.of());

        recorder(OUTCOME, MAINTENANCE).recordDue();

        // 기준 가격 구간이 보존 기간(90분) 안에 있어야 하고, 60분 시점 + 확인 구간 3분이 지나야 한다
        Instant earliest = NOW.minus(Duration.ofMinutes(87));
        verify(anomalies).findMissingPricePoint(earliest, NOW.minus(Duration.ofMinutes(63)), 60, Limit.of(500));
        verify(anomalies).findMissingPricePoint(earliest, NOW.minus(Duration.ofMinutes(8)), 5, Limit.of(500));
    }

    @Test
    void doesNothingWhenNoOffsetsAreConfigured() {
        OutcomeProperties off = new OutcomeProperties(Duration.ofMinutes(30), WINDOW, Duration.ofMinutes(1), 2, "", 1);

        assertThat(recorder(off, MAINTENANCE).recordDue()).isZero();

        verify(anomalies, never()).findMissingPricePoint(any(), any(), anyInt(), any());
    }

    @Test
    void refusesToStartWhenSnapshotsExpireBeforeTheLastPoint() {
        MaintenanceProperties tooShort = new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofMinutes(60));

        assertThatThrownBy(() -> recorder(OUTCOME, tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("snapshot-retention");
    }

    @SuppressWarnings("unchecked")
    private List<SignalPricePoint> savedPoints() {
        ArgumentCaptor<List<SignalPricePoint>> captor = ArgumentCaptor.forClass(List.class);
        verify(points).saveAll(captor.capture());
        return captor.getValue();
    }

    private static AnomalyRecord signal(String code) {
        AnomalyRecord record = new AnomalyRecord(code, RuleType.PRICE_SPIKE, Severity.WARNING, "급변동", SIGNAL_AT);
        ReflectionTestUtils.setField(record, "id", 7L);
        return record;
    }
}
