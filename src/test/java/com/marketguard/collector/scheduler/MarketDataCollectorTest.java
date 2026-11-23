package com.marketguard.collector.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.MarketSession;
import com.marketguard.collector.SymbolUniverse;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.DetectionRuntimeProperties;
import com.marketguard.config.PriceSpikeProperties;
import com.marketguard.config.ScanProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketDataCollectorTest {

    private static final PriceSpikeProperties PRICE_SPIKE =
            new PriceSpikeProperties(new BigDecimal("3.0"), 20, Duration.ofMinutes(30));
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");

    @Test
    void readsHistoryForAWholeChunkInOneQueryAndGivesEachStockItsOwnRows() {
        MarketSession marketSession = mock(MarketSession.class);
        when(marketSession.isKrxOpen()).thenReturn(true);
        SymbolUniverse universe = mock(SymbolUniverse.class);
        when(universe.all()).thenReturn(List.of("000660", "035720"));
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        when(client.fetchPrices(List.of("000660", "035720"))).thenReturn(List.of(
                new MarketPrice("000660", new BigDecimal("200000"), NOW),
                new MarketPrice("035720", new BigDecimal("50000"), NOW)));
        PriceSnapshotRepository snapshots = mock(PriceSnapshotRepository.class);
        when(snapshots.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        List<PriceSnapshot> history = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            history.add(new PriceSnapshot("000660", new BigDecimal("199000"), NOW.minusSeconds(i * 20L)));
        }
        history.add(new PriceSnapshot("035720", new BigDecimal("49000"), NOW.minusSeconds(30)));
        history.sort(Comparator.comparing(PriceSnapshot::getCapturedAt).reversed());
        when(snapshots.findRecentForCodes(List.of("000660", "035720"), NOW.minus(Duration.ofMinutes(30))))
                .thenReturn(history);
        RuleEngine ruleEngine = mock(RuleEngine.class);
        when(ruleEngine.evaluate(any())).thenReturn(List.of());
        CollectorObservability observability = mock(CollectorObservability.class);
        when(observability.startScan()).thenReturn(mock(Timer.Sample.class));
        MarketDataCollector collector = new MarketDataCollector(
                new TossApiProperties("https://example.test", "https://example.test/token",
                        "client", "secret", List.of()),
                new CollectorProperties(true, 30_000), marketSession, universe, client, snapshots,
                mock(AnomalyDispatcher.class), ruleEngine, Clock.fixed(NOW, ZoneOffset.UTC), observability,
                new ScanProperties("", true), new DetectionRuntimeProperties(600_000), PRICE_SPIKE);

        collector.scan();

        verify(snapshots, times(1)).findRecentForCodes(any(), any());
        verify(snapshots, never()).findByStockCodeOrderByCapturedAtDesc(any(), any());
        ArgumentCaptor<DetectionContext> contexts = ArgumentCaptor.forClass(DetectionContext.class);
        verify(ruleEngine, times(2)).evaluate(contexts.capture());
        DetectionContext hynix = contexts.getAllValues().get(0);
        DetectionContext kakao = contexts.getAllValues().get(1);
        // 종목별로 최신 49개만(방금 저장한 현재가 제외), 다른 종목 행은 섞이지 않는다.
        assertThat(hynix.recent()).hasSize(49).allMatch(price -> price.stockCode().equals("000660"));
        assertThat(hynix.recent().get(0).capturedAt()).isEqualTo(NOW.minusSeconds(20));
        assertThat(kakao.recent()).singleElement().satisfies(price -> assertThat(price.stockCode()).isEqualTo("035720"));
    }

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
                mock(AnomalyDispatcher.class),
                mock(RuleEngine.class),
                Clock.systemUTC(),
                observability,
                new ScanProperties("", true),
                new DetectionRuntimeProperties(600_000),
                PRICE_SPIKE);

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
