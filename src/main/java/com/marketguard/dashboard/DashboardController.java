package com.marketguard.dashboard;

import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관제 대시보드용 조회 API. (Phase 3에서 화면/WebSocket 연동 예정)
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AnomalyRepository anomalyRepository;
    private final PriceSnapshotRepository snapshotRepository;

    public DashboardController(AnomalyRepository anomalyRepository,
                              PriceSnapshotRepository snapshotRepository) {
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
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
