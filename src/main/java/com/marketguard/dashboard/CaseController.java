package com.marketguard.dashboard;

import com.marketguard.application.CaseReviewService;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/cases")
public class CaseController {

    private final CaseQueryService queryService;
    private final CaseReviewService reviewService;

    public CaseController(CaseQueryService queryService, CaseReviewService reviewService) {
        this.queryService = queryService;
        this.reviewService = reviewService;
    }

    @GetMapping
    public PageView<CaseSummaryView> cases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) RuleType ruleType,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @Pattern(regexp = "\\d{6}") String stockCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) @Min(0) @Max(100) Integer minimumScore,
            @RequestParam(required = false) @Min(0) @Max(100) Integer maximumScore,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "lastDetectedAt")
            @Pattern(regexp = "lastDetectedAt|firstDetectedAt|score|stockCode|status") String sort,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String direction) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (minimumScore != null && maximumScore != null && minimumScore > maximumScore) {
            throw new IllegalArgumentException("minimumScore must not exceed maximumScore");
        }
        Sort.Direction order = "asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageView.from(queryService.find(status, ruleType, severity, stockCode, from, to,
                minimumScore, maximumScore, PageRequest.of(page, size, Sort.by(order, sort))));
    }

    @GetMapping("/{id}")
    public CaseDetailView detail(@PathVariable @Min(1) Long id) {
        return queryService.detail(id);
    }

    @PatchMapping("/{id}/status")
    public CaseSummaryView updateStatus(@PathVariable @Min(1) Long id,
                                        @Valid @RequestBody StatusUpdateRequest request,
                                        Principal principal) {
        return CaseSummaryView.from(reviewService.updateStatus(id, request.version(), request.status(),
                request.reason(), request.detail(), actor(principal)));
    }

    @PostMapping("/{id}/notes")
    public CaseNoteView addNote(@PathVariable @Min(1) Long id,
                                @Valid @RequestBody NoteRequest request,
                                Principal principal) {
        return CaseNoteView.from(reviewService.addNote(id, request.note(), actor(principal)));
    }

    private static String actor(Principal principal) {
        return principal == null ? "local-operator" : principal.getName();
    }
}
