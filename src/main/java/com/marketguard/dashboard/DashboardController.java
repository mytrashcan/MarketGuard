package com.marketguard.dashboard;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.BoardItem;
import com.marketguard.collector.MarketRankingService;
import com.marketguard.collector.MarketRankingType;
import com.marketguard.collector.StockReferenceService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.detection.model.Candle;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.audit.AuditLogRepository;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관제 대시보드용 조회 API.
 */
@Validated
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final AnomalyRepository anomalyRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final TossMarketDataClient marketDataClient;
    private final BoardDataService boardDataService;
    private final MarketRankingService marketRankingService;
    private final AuditLogRepository auditLogRepository;
    private final StockReferenceService stockReferenceService;

    public DashboardController(AnomalyRepository anomalyRepository,
                              PriceSnapshotRepository snapshotRepository,
                              TossMarketDataClient marketDataClient,
                              BoardDataService boardDataService,
                              MarketRankingService marketRankingService,
                              AuditLogRepository auditLogRepository,
                              StockReferenceService stockReferenceService) {
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.marketDataClient = marketDataClient;
        this.boardDataService = boardDataService;
        this.marketRankingService = marketRankingService;
        this.auditLogRepository = auditLogRepository;
        this.stockReferenceService = stockReferenceService;
    }

    /**
     * 시세 보드 카드(종목 블록) 목록 — 현재가(장 마감 시 종가) + 전일 기준가 대비 등락률 + 거래량 + 호가 잔량.
     * API 키만 있으면 컬렉터 여부와 무관하게 동작한다.
     */
    @GetMapping("/prices/live")
    public List<BoardItem> livePrices() {
        return boardDataService.currentBoard();
    }

    /** Cached Toss market ranking for the main dashboard. */
    @GetMapping("/rankings")
    public List<RankingView> rankings(
            @RequestParam(defaultValue = "MARKET_TRADING_AMOUNT") MarketRankingType type,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return marketRankingService.current(type).stream()
                .limit(limit)
                .map(quote -> RankingView.from(quote, stockReferenceService.nameOf(quote.stockCode())))
                .toList();
    }

    /** 캔들(봉) 차트 데이터. interval=1m|1d, count 1~200 */
    @GetMapping("/stocks/{code}/candles")
    public List<Candle> candles(@PathVariable @Pattern(regexp = "\\d{6}") String code,
                                @RequestParam(defaultValue = "1m")
                                @Pattern(regexp = "1m|1d") String interval,
                                @RequestParam(defaultValue = "60") @Min(1) @Max(200) int count) {
        return marketDataClient.fetchCandles(code, interval, count);
    }

    /** 최근 탐지된 이상거래 목록 */
    @GetMapping("/anomalies")
    public List<AnomalyView> recentAnomalies(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return anomalyRepository.findByOrderByDetectedAtDesc(Limit.of(limit)).stream()
                .map(record -> AnomalyView.from(record, stockReferenceService.nameOf(record.getStockCode())))
                .toList();
    }

    /** 구조화된 근거를 포함한 단일 탐지 상세. */
    @GetMapping("/anomalies/{id}")
    public AnomalyView anomaly(@PathVariable @Min(1) Long id) {
        var record = anomalyRepository.findById(id)
                .orElseThrow(() -> new com.marketguard.application.CaseNotFoundException(id));
        return AnomalyView.from(record, stockReferenceService.nameOf(record.getStockCode()));
    }

    /** 최근 감사 로그(토큰 발급 등 주요 작업 추적) */
    @GetMapping("/audit")
    public List<AuditLogView> recentAudit(
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int limit) {
        return auditLogRepository.findByOrderByCreatedAtDesc(Limit.of(limit)).stream()
                .map(AuditLogView::from)
                .toList();
    }

    /** 특정 종목의 최근 시세 스냅샷 */
    @GetMapping("/stocks/{code}/snapshots")
    public List<PriceSnapshotView> snapshots(
            @PathVariable @Pattern(regexp = "\\d{6}") String code,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return snapshotRepository.findByStockCodeOrderByCapturedAtDesc(code, Limit.of(limit)).stream()
                .map(PriceSnapshotView::from)
                .toList();
    }
}
