package com.marketguard.collector.scheduler;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.detection.model.OrderbookSnapshot;
import com.marketguard.detection.model.PriceLimit;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 감시 대상 종목의 시세·호가·캔들·가격제한폭을 주기적으로 수집하고,
 * 룰 엔진으로 이상거래를 탐지한 뒤 알림(notifier)으로 전파한다.
 */
@Slf4j
@Component
public class MarketDataCollector {

    private static final int RECENT_WINDOW = 50;
    private static final String CANDLE_INTERVAL = "1m";
    private static final int CANDLE_COUNT = 30;

    private final TossApiProperties tossProps;
    private final CollectorProperties collectorProps;
    private final TossMarketDataClient marketDataClient;
    private final PriceSnapshotRepository snapshotRepository;
    private final AnomalyRepository anomalyRepository;
    private final RuleEngine ruleEngine;
    private final List<AnomalyNotifier> notifiers;

    public MarketDataCollector(TossApiProperties tossProps,
                               CollectorProperties collectorProps,
                               TossMarketDataClient marketDataClient,
                               PriceSnapshotRepository snapshotRepository,
                               AnomalyRepository anomalyRepository,
                               RuleEngine ruleEngine,
                               List<AnomalyNotifier> notifiers) {
        this.tossProps = tossProps;
        this.collectorProps = collectorProps;
        this.marketDataClient = marketDataClient;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
        this.ruleEngine = ruleEngine;
        this.notifiers = notifiers;
    }

    @Scheduled(fixedRateString = "${collector.poll-interval-ms}")
    public void collect() {
        if (!collectorProps.enabled()) {
            return;   // API 키 미설정 등으로 비활성화된 경우 조용히 건너뜀
        }
        List<String> watchList = tossProps.watchList();
        if (watchList == null || watchList.isEmpty()) {
            return;
        }

        List<PriceSnapshot> fetched;
        try {
            fetched = marketDataClient.fetchPrices(watchList);   // 배치로 한 번에 조회
        } catch (Exception e) {
            // 외부 API 호출 실패는 이번 사이클만 건너뜀 (Phase 4에서 Resilience4j로 강화)
            log.warn("시세 배치 수집 실패: {}", e.getMessage());
            return;
        }

        for (PriceSnapshot snapshot : fetched) {
            try {
                detect(snapshot);
            } catch (Exception e) {
                log.warn("[{}] 탐지 처리 실패: {}", snapshot.getStockCode(), e.getMessage());
            }
        }
    }

    private void detect(PriceSnapshot fetched) {
        PriceSnapshot saved = snapshotRepository.save(fetched);
        String symbol = saved.getStockCode();

        List<PriceSnapshot> recent = snapshotRepository
                .findByStockCodeOrderByCapturedAtDesc(symbol, Limit.of(RECENT_WINDOW))
                .stream()
                .filter(snapshot -> !snapshot.getId().equals(saved.getId()))
                .toList();

        // 각 부가 데이터는 실패하더라도 해당 룰만 비활성화되도록 개별 격리
        PriceLimit priceLimit = safe(() -> marketDataClient.fetchPriceLimit(symbol), null, symbol, "가격제한폭");
        OrderbookSnapshot orderbook = safe(() -> marketDataClient.fetchOrderbook(symbol), null, symbol, "호가");
        List<Candle> candles = safe(
                () -> marketDataClient.fetchCandles(symbol, CANDLE_INTERVAL, CANDLE_COUNT), List.of(), symbol, "캔들");

        DetectionContext context = new DetectionContext(saved, recent, priceLimit, orderbook, candles);
        List<Anomaly> anomalies = ruleEngine.evaluate(context);

        for (Anomaly anomaly : anomalies) {
            anomalyRepository.save(AnomalyRecord.from(anomaly));
            notifiers.forEach(notifier -> notifier.publish(anomaly));
            log.info("[이상거래 탐지] {} {} - {}",
                    anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
        }
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
