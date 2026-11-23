package com.marketguard.collector.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRecord;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AnomalyDispatcherTest {

    private static final Duration COOLDOWN = Duration.ofMinutes(10);
    private static final Anomaly ANOMALY = new Anomaly(
            "005930", RuleType.PRICE_SPIKE, Severity.WARNING, "가격 급변동",
            Instant.parse("2026-07-31T01:00:00Z"));

    private final AnomalyRecordingService recordingService = mock(AnomalyRecordingService.class);
    private final CollectorObservability observability = mock(CollectorObservability.class);

    @Test
    void publishesToEveryNotifierEvenWhenOneFails() {
        AnomalyNotifier failing = mock(AnomalyNotifier.class);
        AnomalyNotifier healthy = mock(AnomalyNotifier.class);
        doThrow(new IllegalStateException("broker down")).when(failing).publish(ANOMALY);
        when(recordingService.recordIfEligible(ANOMALY, COOLDOWN))
                .thenReturn(Optional.of(mock(AnomalyRecord.class)));
        AnomalyDispatcher dispatcher = new AnomalyDispatcher(
                recordingService, List.of(failing, healthy), observability);

        boolean dispatched = dispatcher.dispatch(ANOMALY, COOLDOWN);

        assertThat(dispatched).isTrue();
        verify(healthy).publish(ANOMALY);
        verify(observability).recordAnomaly();
    }

    @Test
    void doesNotNotifyWhenCooldownSuppressesTheSignal() {
        AnomalyNotifier notifier = mock(AnomalyNotifier.class);
        when(recordingService.recordIfEligible(ANOMALY, COOLDOWN)).thenReturn(Optional.empty());
        AnomalyDispatcher dispatcher = new AnomalyDispatcher(
                recordingService, List.of(notifier), observability);

        boolean dispatched = dispatcher.dispatch(ANOMALY, COOLDOWN);

        assertThat(dispatched).isFalse();
        verifyNoInteractions(notifier);
        verify(observability, never()).recordAnomaly();
    }
}
