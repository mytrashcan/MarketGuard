package com.marketguard.dashboard;

import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final SurveillanceCaseRepository caseRepository;

    public AnalyticsController(SurveillanceCaseRepository caseRepository) {
        this.caseRepository = caseRepository;
    }

    @GetMapping("/rules")
    public List<RuleAnalyticsView> rules() {
        return caseRepository.analyzeRules().stream().map(RuleAnalyticsView::from).toList();
    }
}
