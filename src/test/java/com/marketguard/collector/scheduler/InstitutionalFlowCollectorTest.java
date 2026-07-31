package com.marketguard.collector.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.InstitutionalFlowProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.detection.rule.InstitutionalFlowDetector;
import com.marketguard.domain.anomaly.AnomalyRecord;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InstitutionalFlowCollectorTest {

    private static final Instant NOW = Instant.parse("2026-07-31T06:20:00Z");
    private static final InstitutionalFlowProperties FLOW_PROPERTIES =
            new InstitutionalFlowProperties(
                    new BigDecimal("3"), 20, 5, new BigDecimal("100000000000"),
                    Duration.ofMinutes(30), Duration.ofHours(6));

    @Test
    void recordsAndPublishesAnEligibleMarketSignal() {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        InstitutionalFlowDetector detector = mock(InstitutionalFlowDetector.class);
        AnomalyRecordingService recordingService = mock(AnomalyRecordingService.class);
        AnomalyNotifier notifier = mock(AnomalyNotifier.class);
        CollectorObservability observability = mock(CollectorObservability.class);
        List<InstitutionalTradingRecord> records = List.of();
        Anomaly anomaly = new Anomaly(
                "KOSPI", RuleType.INSTITUTIONAL_NET_BUY_SURGE, Severity.WARNING,
                "기관 순매수 급증", NOW);
        when(client.fetchInstitutionalTrading("KOSPI", 21)).thenReturn(records);
        when(client.fetchInstitutionalTrading("KOSDAQ", 21)).thenReturn(records);
        when(detector.evaluate(any())).thenAnswer(invocation ->
                invocation.<com.marketguard.detection.model.InstitutionalFlowContext>getArgument(0)
                                .marketSymbol().equals("KOSPI")
                        ? Optional.of(anomaly) : Optional.empty());
        when(recordingService.recordIfEligible(anomaly, Duration.ofHours(6)))
                .thenReturn(Optional.of(mock(AnomalyRecord.class)));
        InstitutionalFlowCollector collector = new InstitutionalFlowCollector(
                new CollectorProperties(true, 30_000),
                FLOW_PROPERTIES,
                client,
                detector,
                recordingService,
                List.of(notifier),
                observability,
                Clock.fixed(NOW, ZoneOffset.UTC));

        collector.scan();

        verify(client).fetchInstitutionalTrading("KOSPI", 21);
        verify(client).fetchInstitutionalTrading("KOSDAQ", 21);
        verify(recordingService).recordIfEligible(anomaly, Duration.ofHours(6));
        verify(notifier).publish(anomaly);
        verify(observability).recordAnomaly();
    }

    @Test
    void doesNothingWhenCollectionIsDisabled() {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        InstitutionalFlowDetector detector = mock(InstitutionalFlowDetector.class);
        AnomalyRecordingService recordingService = mock(AnomalyRecordingService.class);
        InstitutionalFlowCollector collector = new InstitutionalFlowCollector(
                new CollectorProperties(false, 30_000),
                FLOW_PROPERTIES,
                client,
                detector,
                recordingService,
                List.of(),
                mock(CollectorObservability.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        collector.scan();

        verifyNoInteractions(client, detector, recordingService);
    }

    @Test
    void doesNotPublishSignalsRejectedByTheCooldown() {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        InstitutionalFlowDetector detector = mock(InstitutionalFlowDetector.class);
        AnomalyRecordingService recordingService = mock(AnomalyRecordingService.class);
        AnomalyNotifier notifier = mock(AnomalyNotifier.class);
        Anomaly anomaly = new Anomaly(
                "KOSPI", RuleType.INSTITUTIONAL_NET_BUY_SURGE, Severity.WARNING,
                "기관 순매수 급증", NOW);
        when(detector.evaluate(any())).thenReturn(Optional.of(anomaly));
        when(recordingService.recordIfEligible(anomaly, Duration.ofHours(6)))
                .thenReturn(Optional.empty());
        InstitutionalFlowCollector collector = new InstitutionalFlowCollector(
                new CollectorProperties(true, 30_000),
                FLOW_PROPERTIES,
                client,
                detector,
                recordingService,
                List.of(notifier),
                mock(CollectorObservability.class),
                Clock.fixed(NOW, ZoneOffset.UTC));

        collector.scan();

        verify(notifier, never()).publish(anomaly);
    }
}
