package com.marketguard.collector.scheduler;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.collector.MarketSession;
import com.marketguard.collector.SymbolUniverse;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.detection.model.Warning;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final AnomalyRepository anomalyRepository;
    private final RuleEngine ruleEngine;
    private final List<AnomalyNotifier> notifiers;
    private final boolean marketHoursOnly;
    private final long cooldownMs;

    /** (종목|룰) → 마지막 발생 시각. 동일 신호 중복 억제용. */
    private final Map<String, Instant> lastEmitted = new ConcurrentHashMap<>();

    public MarketDataCollector(TossApiProperties tossProps,
                               CollectorProperties collectorProps,
                               MarketSession marketSession,
                               SymbolUniverse symbolUniverse,
                               TossMarketDataClient marketDataClient,
                               PriceSnapshotRepository snapshotRepository,
                               AnomalyRepository anomalyRepository,
                               RuleEngine ruleEngine,
                               List<AnomalyNotifier> notifiers,
                               @Value("${scan.market-hours-only:true}") boolean marketHoursOnly,
                               @Value("${detection.cooldown-ms:600000}") long cooldownMs) {
        this.tossProps = tossProps;
        this.collectorProps = collectorProps;
        this.marketSession = marketSession;
        this.symbolUniverse = symbolUniverse;
        this.marketDataClient = marketDataClient;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
        this.ruleEngine = ruleEngine;
        this.notifiers = notifiers;
        this.marketHoursOnly = marketHoursOnly;
        this.cooldownMs = cooldownMs;
    }

    @Scheduled(fixedRateString = "${collector.poll-interval-ms}")
    public void scan() {
        if (!collectorProps.enabled()) {
            return;   // API 키 미설정 등으로 비활성화된 경우 조용히 건너뜀
        }
        if (marketHoursOnly && !marketSession.isKrxOpen()) {
            // 장 마감/주말에는 캔들·호가가 직전 장 데이터라 오탐이 나므로 스캔 생략
            log.debug("정규장 시간이 아니라 이상거래 스캔을 건너뜁니다.");
            return;
        }

        Set<String> focus = new LinkedHashSet<>(
                tossProps.watchList() == null ? List.of() : tossProps.watchList());

        // 1) 넓은 스캔: 유니버스에서 포커스를 뺀 나머지를 200개씩 배치로 가격만 조회 → 가격기반 룰
        List<String> broad = symbolUniverse.all().stream()
                .filter(symbol -> !focus.contains(symbol))
                .toList();
        for (List<String> chunk : SymbolUniverse.chunk(broad, SymbolUniverse.MAX_PER_REQUEST)) {
            for (PriceSnapshot snapshot : fetchPrices(chunk)) {
                process(snapshot, false);
            }
        }

        // 2) 깊은 스캔: 포커스 종목은 호가·캔들·가격제한폭까지 받아 모든 룰 적용
        for (PriceSnapshot snapshot : fetchPrices(List.copyOf(focus))) {
            process(snapshot, true);
        }
    }

    private List<PriceSnapshot> fetchPrices(List<String> symbols) {
        if (symbols.isEmpty()) {
            return List.of();
        }
        try {
            return marketDataClient.fetchPrices(symbols);
        } catch (Exception e) {
            // 한 배치 실패가 다른 배치를 멈추지 않도록 격리 (Phase 4에서 Resilience4j로 강화)
            log.warn("시세 배치 조회 실패({}종목): {}", symbols.size(), e.getMessage());
            return List.of();
        }
    }

    private void process(PriceSnapshot fetched, boolean deep) {
        try {
            PriceSnapshot saved = snapshotRepository.save(fetched);
            String symbol = saved.getStockCode();

            List<PriceSnapshot> recent = snapshotRepository
                    .findByStockCodeOrderByCapturedAtDesc(symbol, Limit.of(RECENT_WINDOW))
                    .stream()
                    .filter(snapshot -> !snapshot.getId().equals(saved.getId()))
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

            DetectionContext context = new DetectionContext(saved, recent, priceLimit, orderbook, candles, warnings);
            for (Anomaly anomaly : ruleEngine.evaluate(context)) {
                if (onCooldown(anomaly)) {
                    continue;   // 같은 (종목,룰) 신호가 쿨다운 내면 중복 발생 억제
                }
                anomalyRepository.save(AnomalyRecord.from(anomaly));
                notifiers.forEach(notifier -> notifier.publish(anomaly));
                log.info("[이상거래 탐지] {} {} - {}",
                        anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
            }
        } catch (Exception e) {
            log.warn("[{}] 탐지 처리 실패: {}", fetched.getStockCode(), e.getMessage());
        }
    }

    private boolean onCooldown(Anomaly anomaly) {
        String key = anomaly.stockCode() + "|" + anomaly.ruleType();
        Instant now = Instant.now();
        Instant last = lastEmitted.get(key);
        if (last != null && Duration.between(last, now).toMillis() < cooldownMs) {
            return true;
        }
        lastEmitted.put(key, now);
        return false;
    }

    private <T> T safe(Supplier<T> call, T fallback, String symbol, String what) {
        try {
            return call.get();
        } catch (Exception e) {
            log.warn("[{}] {} 조회 실패: {}", symbol, what, e.getMessage());
            return fallback;
        }
    }
}
