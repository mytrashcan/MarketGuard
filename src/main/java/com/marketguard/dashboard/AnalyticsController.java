package com.marketguard.dashboard;

import com.marketguard.domain.casework.SurveillanceCaseRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
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

    public AnalyticsController(SurveillanceCaseRepository caseRepository,
                               SignalAnalyticsService signalAnalyticsService) {
        this.caseRepository = caseRepository;
        this.signalAnalyticsService = signalAnalyticsService;
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
}
