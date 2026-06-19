package com.marketguard.collector.scheduler;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.CollectorProperties;
import com.marketguard.config.TossApiProperties;
import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.DetectionContext;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 감시 대상 종목의 시세를 주기적으로 배치 수집하고, 룰 엔진으로 이상거래를 탐지한다.
 */
@Slf4j
@Component
public class MarketDataCollector {

    private static final int RECENT_WINDOW = 50;

    private final TossApiProperties tossProps;
    private final CollectorProperties collectorProps;
    private final TossMarketDataClient marketDataClient;
    private final PriceSnapshotRepository snapshotRepository;
    private final AnomalyRepository anomalyRepository;
    private final RuleEngine ruleEngine;

    public MarketDataCollector(TossApiProperties tossProps,
                               CollectorProperties collectorProps,
                               TossMarketDataClient marketDataClient,
                               PriceSnapshotRepository snapshotRepository,
                               AnomalyRepository anomalyRepository,
                               RuleEngine ruleEngine) {
        this.tossProps = tossProps;
        this.collectorProps = collectorProps;
        this.marketDataClient = marketDataClient;
        this.snapshotRepository = snapshotRepository;
        this.anomalyRepository = anomalyRepository;
        this.ruleEngine = ruleEngine;
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

        List<PriceSnapshot> recent = snapshotRepository
                .findByStockCodeOrderByCapturedAtDesc(saved.getStockCode(), Limit.of(RECENT_WINDOW))
                .stream()
                .filter(snapshot -> !snapshot.getId().equals(saved.getId()))
                .toList();

        List<Anomaly> anomalies = ruleEngine.evaluate(new DetectionContext(saved, recent));
        for (Anomaly anomaly : anomalies) {
            anomalyRepository.save(AnomalyRecord.from(anomaly));
            log.info("[이상거래 탐지] {} {} - {}",
                    anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
        }
    }
}
