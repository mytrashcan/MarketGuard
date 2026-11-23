package com.marketguard.collector.scheduler;

import com.marketguard.collector.MarketSession;
import com.marketguard.collector.SymbolUniverse;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.DetectionRuntimeProperties;
import com.marketguard.config.PriceSpikeProperties;
import com.marketguard.config.ScanProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 2단계 시장 스캐너. 배치(최대 200종목)마다 직전 스냅샷을 한 번에 읽어 종목별 조회를 피한다.
 * - 넓게(cheap): 전 종목 유니버스를 배치(최대 200개)로 가격만 받아 가격기반 룰(가격 급변동)로 스캔.
 * - 깊게(deep): 포커스 종목(watch-list)만 호가·캔들·가격제한폭까지 받아 모든 룰 적용.
 * 정규장 시간에만 동작하며(장 마감 중 스테일 데이터 오탐 방지), 같은 (종목,룰) 신호는 쿨다운으로 중복 억제한다.
 */
@Slf4j
@Component
public class MarketDataCollector {

    /** 종목별로 규칙에 넘기는 직전 스냅샷 수(방금 저장한 현재가 제외). */
    private static final int RECENT_WINDOW = 49;
    private static final String CANDLE_INTERVAL = "1m";
    private static final int CANDLE_COUNT = 30;

    private final TossApiProperties tossProps;
    private final CollectorProperties collectorProps;
    private final MarketSession marketSession;
    private final SymbolUniverse symbolUniverse;
    private final TossMarketDataClient marketDataClient;
    private final PriceSnapshotRepository snapshotRepository;
    private final AnomalyDispatcher dispatcher;
    private final RuleEngine ruleEngine;
    private final boolean marketHoursOnly;
    private final Duration cooldown;
    /** 가격 규칙은 이보다 오래된 스냅샷을 쓰지 않으므로 이 구간만 읽는다. */
    private final Duration recentHorizon;
    private final Clock clock;
    private final CollectorObservability observability;
    private final AtomicBoolean running = new AtomicBoolean();

    public MarketDataCollector(TossApiProperties tossProps,
                               CollectorProperties collectorProps,
                               MarketSession marketSession,
                               SymbolUniverse symbolUniverse,
                               TossMarketDataClient marketDataClient,
                               PriceSnapshotRepository snapshotRepository,
                               AnomalyDispatcher dispatcher,
                               RuleEngine ruleEngine,
                               Clock clock,
                               CollectorObservability observability,
                               ScanProperties scanProperties,
                               DetectionRuntimeProperties detectionProperties,
                               PriceSpikeProperties priceSpikeProperties) {
        this.tossProps = tossProps;
        this.collectorProps = collectorProps;
        this.marketSession = marketSession;
        this.symbolUniverse = symbolUniverse;
        this.marketDataClient = marketDataClient;
        this.snapshotRepository = snapshotRepository;
        this.dispatcher = dispatcher;
        this.ruleEngine = ruleEngine;
        this.clock = clock;
        this.observability = observability;
        this.marketHoursOnly = scanProperties.marketHoursOnly();
        this.cooldown = Duration.ofMillis(detectionProperties.cooldownMs());
        this.recentHorizon = priceSpikeProperties.maxAge();
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

            Set<String> focus = focusSymbols();
            List<String> broad = symbolUniverse.all().stream()
                    .filter(symbol -> !focus.contains(symbol))
                    .toList();
            for (List<String> chunk : SymbolUniverse.chunk(broad, SymbolUniverse.MAX_PER_REQUEST)) {
                scanBatch(chunk, false);
            }
            scanBatch(List.copyOf(focus), true);
        } catch (RuntimeException exception) {
            outcome = "failure";
            log.error("Collector scan failed ({})", exception.getClass().getSimpleName());
        } finally {
            observability.finishScan(sample, outcome);
            running.set(false);
        }
    }

    private Set<String> focusSymbols() {
        return new LinkedHashSet<>(tossProps.watchList());
    }

    /** 한 배치의 현재가를 받아 종목별로 탐지한다. deep이면 호가·캔들 등 부가 데이터까지 사용한다. */
    private void scanBatch(List<String> symbols, boolean deep) {
        List<MarketPrice> prices = fetchPrices(symbols);
        observability.recordProcessedPrices(prices.size(), deep);
        if (prices.isEmpty()) {
            return;
        }
        // 새 현재가를 저장하기 전에 읽으므로 방금 저장할 스냅샷은 자연히 빠진다.
        Map<String, List<MarketPrice>> recent = loadRecent(prices);
        prices.forEach(price -> process(price, deep, recent.getOrDefault(price.stockCode(), List.of())));
    }

    /**
     * 배치의 모든 종목에 대해 직전 스냅샷을 한 쿼리로 읽고 종목별 최신 RECENT_WINDOW개로 나눈다.
     * 실패하면 이력 없이 진행한다(스냅샷 저장은 계속되고, 이력이 필요한 규칙만 이번에 건너뛴다).
     */
    private Map<String, List<MarketPrice>> loadRecent(List<MarketPrice> prices) {
        try {
            List<String> codes = prices.stream().map(MarketPrice::stockCode).distinct().toList();
            Map<String, List<MarketPrice>> bySymbol = new HashMap<>();
            List<PriceSnapshot> rows = snapshotRepository.findRecentForCodes(
                    codes, clock.instant().minus(recentHorizon), RECENT_WINDOW);
            for (PriceSnapshot snapshot : rows) {
                bySymbol.computeIfAbsent(snapshot.getStockCode(), code -> new ArrayList<>()).add(snapshot.toDomain());
            }
            return bySymbol;
        } catch (Exception e) {
            observability.recordBatchFailure();
            log.warn("직전 스냅샷 배치 조회 실패({}종목): {}", prices.size(), e.getClass().getSimpleName());
            return Map.of();
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

    private void process(MarketPrice fetched, boolean deep, List<MarketPrice> recent) {
        try {
            DetectionContext context = buildContext(fetched, deep, recent);
            ruleEngine.evaluate(context).forEach(anomaly -> dispatcher.dispatch(anomaly, cooldown));
        } catch (Exception e) {
            observability.recordItemFailure();
            log.warn("[{}] 탐지 처리 실패: {}", fetched.stockCode(), e.getClass().getSimpleName());
        }
    }

    /** 현재가를 스냅샷으로 저장하고, 직전 스냅샷과(필요하면) 부가 시장 데이터로 탐지 컨텍스트를 만든다. */
    private DetectionContext buildContext(MarketPrice fetched, boolean deep, List<MarketPrice> recent) {
        PriceSnapshot saved = snapshotRepository.save(PriceSnapshot.from(fetched));
        String symbol = saved.getStockCode();
        if (!deep) {
            return new DetectionContext(saved.toDomain(), recent, null, null, List.of(), List.of(), clock.instant());
        }
        // 부가 데이터는 실패하더라도 해당 룰만 비활성화되도록 개별 격리
        PriceLimit priceLimit = safe(() -> marketDataClient.fetchPriceLimit(symbol), null, symbol, "가격제한폭");
        OrderbookSnapshot orderbook = safe(() -> marketDataClient.fetchOrderbook(symbol), null, symbol, "호가");
        List<Candle> candles = safe(() -> marketDataClient.fetchCandles(symbol, CANDLE_INTERVAL, CANDLE_COUNT),
                List.of(), symbol, "캔들");
        List<Warning> warnings = safe(() -> marketDataClient.fetchWarnings(symbol), List.of(), symbol, "투자경고");
        return new DetectionContext(
                saved.toDomain(), recent, priceLimit, orderbook, candles, warnings, clock.instant());
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
