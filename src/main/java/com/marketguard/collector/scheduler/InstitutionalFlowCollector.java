package com.marketguard.collector.scheduler;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.InstitutionalFlowProperties;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.InstitutionalFlowContext;
import com.marketguard.detection.model.InstitutionalTradingRecord;
import com.marketguard.detection.model.MarketInstrument;
import com.marketguard.detection.rule.InstitutionalFlowDetector;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls official market-wide institutional trading totals independently from stock scans. */
@Slf4j
@Component
public class InstitutionalFlowCollector {

    private static final List<String> MARKETS = List.of(MarketInstrument.KOSPI, MarketInstrument.KOSDAQ);

    private final CollectorProperties collectorProperties;
    private final InstitutionalFlowProperties flowProperties;
    private final TossMarketDataClient marketDataClient;
    private final InstitutionalFlowDetector detector;
    private final AnomalyRecordingService recordingService;
    private final List<AnomalyNotifier> notifiers;
    private final CollectorObservability observability;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public InstitutionalFlowCollector(
            CollectorProperties collectorProperties,
            InstitutionalFlowProperties flowProperties,
            TossMarketDataClient marketDataClient,
            InstitutionalFlowDetector detector,
            AnomalyRecordingService recordingService,
            List<AnomalyNotifier> notifiers,
            CollectorObservability observability,
            Clock clock) {
        this.collectorProperties = collectorProperties;
        this.flowProperties = flowProperties;
        this.marketDataClient = marketDataClient;
        this.detector = detector;
        this.recordingService = recordingService;
        this.notifiers = List.copyOf(notifiers);
        this.observability = observability;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${collector.institutional-flow-poll-interval-ms:300000}")
    public void scan() {
        if (!collectorProperties.enabled() || !running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (String market : MARKETS) {
                scanMarket(market);
            }
        } finally {
            running.set(false);
        }
    }

    private void scanMarket(String market) {
        try {
            int count = Math.min(flowProperties.lookback() + 1, 100);
            List<InstitutionalTradingRecord> records =
                    marketDataClient.fetchInstitutionalTrading(market, count);
            Optional<Anomaly> detected = detector.evaluate(
                    new InstitutionalFlowContext(market, records, clock.instant()));
            if (detected.isEmpty()) {
                return;
            }
            Anomaly anomaly = detected.orElseThrow();
            if (recordingService.recordIfEligible(anomaly, flowProperties.cooldown()).isEmpty()) {
                return;
            }
            observability.recordAnomaly();
            for (AnomalyNotifier notifier : notifiers) {
                try {
                    notifier.publish(anomaly);
                } catch (RuntimeException exception) {
                    log.warn("Notifier {} failed for {} {} ({})",
                            notifier.getClass().getSimpleName(), anomaly.stockCode(), anomaly.ruleType(),
                            exception.getClass().getSimpleName());
                }
            }
            log.info("[기관 수급 이상징후] {} {} - {}",
                    anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
        } catch (RuntimeException exception) {
            observability.recordItemFailure();
            log.warn("[{}] 기관 수급 탐지 실패: {}", market, exception.getClass().getSimpleName());
        }
    }
}
