package com.marketguard.dashboard;

import com.marketguard.domain.casework.SurveillanceCaseRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final SurveillanceCaseRepository caseRepository;
    private final SignalAnalyticsService signalAnalyticsService;
    private final PricePathExport pricePathExport;

    public AnalyticsController(SurveillanceCaseRepository caseRepository,
                               SignalAnalyticsService signalAnalyticsService,
                               PricePathExport pricePathExport) {
        this.caseRepository = caseRepository;
        this.signalAnalyticsService = signalAnalyticsService;
        this.pricePathExport = pricePathExport;
    }

    @GetMapping("/rules")
    public List<RuleAnalyticsView> rules() {
        return caseRepository.analyzeRules().stream().map(RuleAnalyticsView::from).toList();
    }

    /** 최근 days일(KST, 오늘 포함)의 신호 수와 사후 결과. */
    @GetMapping("/signals")
    public SignalAnalyticsView signals(@RequestParam(defaultValue = "14") @Min(1) @Max(31) int days) {
        return signalAnalyticsService.summarize(days);
    }

    /** 최근 days일(KST, 오늘 포함) 신호의 가격 경로 원자료. 한 줄이 (신호, 신호 후 시점) 하나다. */
    @GetMapping(value = "/price-paths.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> pricePaths(@RequestParam(defaultValue = "31") @Min(1) @Max(366) int days) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("marketguard-price-paths.csv").build().toString())
                .body(pricePathExport.csv(days));
    }
}
