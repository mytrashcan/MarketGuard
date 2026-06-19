package com.marketguard.dashboard;

import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.TossApiProperties;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관제 대시보드용 조회 API.
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AnomalyRepository anomalyRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final TossApiProperties tossProps;
    private final TossMarketDataClient marketDataClient;

    public DashboardController(AnomalyRepository anomalyRepository,
                              PriceSnapshotRepository snapshotRepository,
                              TossApiProperties tossProps,
                              TossMarketDataClient marketDataClient) {
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.tossProps = tossProps;
        this.marketDataClient = marketDataClient;
    }

    /**
     * 감시 대상(대형주) 현재가 보드. 토스 시세 API를 즉시 호출하므로
     * 컬렉터(collector.enabled) 여부와 무관하게 API 키만 있으면 동작한다.
     */
    @GetMapping("/prices/live")
    public List<PriceView> livePrices() {
        try {
            return marketDataClient.fetchPrices(tossProps.watchList()).stream()
                    .map(snapshot -> new PriceView(
                            snapshot.getStockCode(), snapshot.getPrice(), snapshot.getCapturedAt()))
                    .toList();
        } catch (Exception e) {
            log.warn("실시간 시세 조회 실패: {}", e.getMessage());
            return List.of();   // 키 미설정/장 마감 등은 빈 목록으로 (대시보드가 안내 표시)
        }
    }

    /** 최근 탐지된 이상거래 목록 */
    @GetMapping("/anomalies")
    public List<AnomalyRecord> recentAnomalies() {
        return anomalyRepository.findByOrderByDetectedAtDesc(Limit.of(50));
    }

    /** 특정 종목의 최근 시세 스냅샷 */
    @GetMapping("/stocks/{code}/snapshots")
    public List<PriceSnapshot> snapshots(@PathVariable String code) {
        return snapshotRepository.findByStockCodeOrderByCapturedAtDesc(code, Limit.of(50));
    }
}
