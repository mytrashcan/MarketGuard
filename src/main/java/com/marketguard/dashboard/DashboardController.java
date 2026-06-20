package com.marketguard.dashboard;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.BoardItem;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.detection.model.Candle;
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
import org.springframework.web.bind.annotation.RequestParam;
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
    private final TossMarketDataClient marketDataClient;
    private final BoardDataService boardDataService;

    public DashboardController(AnomalyRepository anomalyRepository,
                              PriceSnapshotRepository snapshotRepository,
                              TossMarketDataClient marketDataClient,
                              BoardDataService boardDataService) {
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.marketDataClient = marketDataClient;
        this.boardDataService = boardDataService;
    }

    /**
     * 시세 보드 카드(종목 블록) 목록 — 현재가(장 마감 시 종가) + 전일 종가 대비 등락률 + 거래량 + 미니 캔들.
     * API 키만 있으면 컬렉터 여부와 무관하게 동작한다.
     */
    @GetMapping("/prices/live")
    public List<BoardItem> livePrices() {
        return boardDataService.currentBoard();
    }

    /** 캔들(봉) 차트 데이터. interval=1m|1d, count 1~200 */
    @GetMapping("/stocks/{code}/candles")
    public List<Candle> candles(@PathVariable String code,
                                @RequestParam(defaultValue = "1m") String interval,
                                @RequestParam(defaultValue = "60") int count) {
        try {
            return marketDataClient.fetchCandles(code, interval, count);
        } catch (Exception e) {
            log.warn("[{}] 캔들 조회 실패: {}", code, e.getMessage());
            return List.of();
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
