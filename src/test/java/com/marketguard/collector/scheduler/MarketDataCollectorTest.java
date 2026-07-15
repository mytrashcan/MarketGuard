package com.marketguard.collector.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.collector.MarketSession;
import com.marketguard.collector.SymbolUniverse;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.DetectionRuntimeProperties;
import com.marketguard.config.ScanProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MarketDataCollectorTest {

    @Test
    void skipsAnOverlappingScanInvocation() throws Exception {
        MarketSession marketSession = mock(MarketSession.class);
        CollectorObservability observability = mock(CollectorObservability.class);
        Timer.Sample sample = mock(Timer.Sample.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(observability.startScan()).thenReturn(sample);
        when(marketSession.isKrxOpen()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return false;
        });
        MarketDataCollector collector = new MarketDataCollector(
                new TossApiProperties("https://example.test", "https://example.test/token",
                        "client", "secret", List.of("005930")),
                new CollectorProperties(true, 30_000),
                marketSession,
                mock(SymbolUniverse.class),
                mock(TossMarketDataClient.class),
                mock(PriceSnapshotRepository.class),
                mock(AnomalyRecordingService.class),
                mock(RuleEngine.class),
                List.<AnomalyNotifier>of(),
                Clock.systemUTC(),
                observability,
                new ScanProperties("", true),
                new DetectionRuntimeProperties(600_000));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> first = executor.submit(collector::scan);
            entered.await(5, TimeUnit.SECONDS);

            collector.scan();
            verify(marketSession, times(1)).isKrxOpen();

            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            verify(observability).finishScan(sample, "market_closed");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }
}
