package com.marketguard.collector.scheduler;

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
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.MarketPrice;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.Warning;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.time.Duration;
import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 2단계 시장 스캐너.
 * - 넓게(cheap): 전 종목 유니버스를 배치(최대 200개)로 가격만 받아 가격기반 룰(가격 급변동)로 스캔.
 * - 깊게(deep): 포커스 종목(watch-list)만 호가·캔들·가격제한폭까지 받아 모든 룰 적용.
 * 정규장 시간에만 동작하며(장 마감 중 스테일 데이터 오탐 방지), 같은 (종목,룰) 신호는 쿨다운으로 중복 억제한다.
 */
@Slf4j
@Component
public class MarketDataCollector {

    private static final int RECENT_WINDOW = 50;
    private static final String CANDLE_INTERVAL = "1m";
    private static final int CANDLE_COUNT = 30;

    private final TossApiProperties tossProps;
    private final CollectorProperties collectorProps;
    private final MarketSession marketSession;
    private final SymbolUniverse symbolUniverse;
    private final TossMarketDataClient marketDataClient;
    private final PriceSnapshotRepository snapshotRepository;
    private final AnomalyRecordingService anomalyRecordingService;
    private final RuleEngine ruleEngine;
    private final List<AnomalyNotifier> notifiers;
    private final boolean marketHoursOnly;
    private final long cooldownMs;
    private final Clock clock;
    private final CollectorObservability observability;
    private final AtomicBoolean running = new AtomicBoolean();

    public MarketDataCollector(TossApiProperties tossProps,
                               CollectorProperties collectorProps,
                               MarketSession marketSession,
                               SymbolUniverse symbolUniverse,
                               TossMarketDataClient marketDataClient,
                               PriceSnapshotRepository snapshotRepository,
                               AnomalyRecordingService anomalyRecordingService,
                               RuleEngine ruleEngine,
                               List<AnomalyNotifier> notifiers,
                               Clock clock,
                               CollectorObservability observability,
                               ScanProperties scanProperties,
                               DetectionRuntimeProperties detectionProperties) {
        this.tossProps = tossProps;
        this.collectorProps = collectorProps;
        this.marketSession = marketSession;
        this.symbolUniverse = symbolUniverse;
        this.marketDataClient = marketDataClient;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRecordingService = anomalyRecordingService;
        this.ruleEngine = ruleEngine;
        this.notifiers = List.copyOf(notifiers);
        this.clock = clock;
        this.observability = observability;
        this.marketHoursOnly = scanProperties.marketHoursOnly();
        this.cooldownMs = detectionProperties.cooldownMs();
    }

    @Scheduled(fixedDelayString = "${collector.poll-interval-ms}")
    public void scan() {
        if (!collectorProps.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Previous collector scan is still running; overlapping invocation skipped");
            return;
        }
        Timer.Sample sample = observability.startScan();
        String outcome = "success";
        try {
            if (marketHoursOnly && !marketSession.isKrxOpen()) {
                log.debug("정규장 시간이 아니라 이상거래 스캔을 건너뜁니다.");
                outcome = "market_closed";
                return;
            }

            Set<String> focus = new LinkedHashSet<>(
                    tossProps.watchList() == null ? List.of() : tossProps.watchList());

            List<String> broad = symbolUniverse.all().stream()
                    .filter(symbol -> !focus.contains(symbol))
                    .toList();
            for (List<String> chunk : SymbolUniverse.chunk(broad, SymbolUniverse.MAX_PER_REQUEST)) {
                List<MarketPrice> prices = fetchPrices(chunk);
                observability.recordProcessedPrices(prices.size(), false);
                for (MarketPrice snapshot : prices) {
                    process(snapshot, false);
                }
            }

            List<MarketPrice> focusPrices = fetchPrices(List.copyOf(focus));
            observability.recordProcessedPrices(focusPrices.size(), true);
            for (MarketPrice snapshot : focusPrices) {
                process(snapshot, true);
            }
        } catch (RuntimeException exception) {
            outcome = "failure";
            log.error("Collector scan failed ({})", exception.getClass().getSimpleName());
        } finally {
            observability.finishScan(sample, outcome);
            running.set(false);
        }
    }

    private List<MarketPrice> fetchPrices(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        try {
            return marketDataClient.fetchPrices(symbols);
        } catch (Exception e) {
            observability.recordBatchFailure();
            log.warn("시세 배치 조회 실패({}종목): {}", symbols.size(), e.getClass().getSimpleName());
            return List.of();
        }
    }

    private void process(MarketPrice fetched, boolean deep) {
        try {
            PriceSnapshot saved = snapshotRepository.save(PriceSnapshot.from(fetched));
            String symbol = saved.getStockCode();

            List<MarketPrice> recent = snapshotRepository
                    .findByStockCodeOrderByCapturedAtDesc(symbol, Limit.of(RECENT_WINDOW))
                    .stream()
                    .filter(snapshot -> !snapshot.getId().equals(saved.getId()))
                    .map(PriceSnapshot::toDomain)
                    .toList();

            PriceLimit priceLimit = null;
            OrderbookSnapshot orderbook = null;
            List<Candle> candles = List.of();
            List<Warning> warnings = List.of();
            if (deep) {
                // 부가 데이터는 실패하더라도 해당 룰만 비활성화되도록 개별 격리
                priceLimit = safe(() -> marketDataClient.fetchPriceLimit(symbol), null, symbol, "가격제한폭");
                orderbook = safe(() -> marketDataClient.fetchOrderbook(symbol), null, symbol, "호가");
                candles = safe(() -> marketDataClient.fetchCandles(symbol, CANDLE_INTERVAL, CANDLE_COUNT),
                        List.of(), symbol, "캔들");
                warnings = safe(() -> marketDataClient.fetchWarnings(symbol), List.of(), symbol, "투자경고");
            }

            DetectionContext context = new DetectionContext(
                    saved.toDomain(), recent, priceLimit, orderbook, candles, warnings, clock.instant());
            for (Anomaly anomaly : ruleEngine.evaluate(context)) {
                if (anomalyRecordingService.recordIfEligible(anomaly, Duration.ofMillis(cooldownMs)).isEmpty()) {
                    continue;
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
                log.info("[이상거래 탐지] {} {} - {}",
                        anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
            }
        } catch (Exception e) {
            observability.recordItemFailure();
            log.warn("[{}] 탐지 처리 실패: {}", fetched.stockCode(), e.getClass().getSimpleName());
        }
    }

    private <T> T safe(Supplier<T> call, T fallback, String symbol, String what) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("[{}] {} 조회 실패: {}", symbol, what, e.getClass().getSimpleName());
            return fallback;
        }
    }
}
