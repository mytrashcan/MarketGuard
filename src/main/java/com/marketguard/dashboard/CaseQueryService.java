package com.marketguard.dashboard;

import com.marketguard.application.CaseNotFoundException;
import com.marketguard.application.port.StockNameResolver;
import com.marketguard.config.SurveillanceProperties;
import com.marketguard.detection.casework.CaseScoreCalculator;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.CompositeScore;
import com.marketguard.detection.model.AnomalyEvidence;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.casework.CaseNoteRepository;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CaseQueryService {

    private final SurveillanceCaseRepository caseRepository;
    private final AnomalyRepository anomalyRepository;
    private final CaseNoteRepository noteRepository;
    private final CaseStatusHistoryRepository historyRepository;
    private final StockNameResolver stockNameResolver;
    private final CaseScoreCalculator scoreCalculator;

    public CaseQueryService(SurveillanceCaseRepository caseRepository,
                            AnomalyRepository anomalyRepository,
                            CaseNoteRepository noteRepository,
                            CaseStatusHistoryRepository historyRepository,
                            StockNameResolver stockNameResolver,
                            SurveillanceProperties properties) {
        this.caseRepository = caseRepository;
        this.anomalyRepository = anomalyRepository;
        this.noteRepository = noteRepository;
        this.historyRepository = historyRepository;
        this.stockNameResolver = stockNameResolver;
        this.scoreCalculator = new CaseScoreCalculator(properties.scoreConfiguration());
    }

    public Page<CaseSummaryView> find(CaseStatus status, RuleType ruleType, Severity severity,
                                      String stockCode, Instant from, Instant to,
                                      Integer minimumScore, Integer maximumScore, Pageable pageable) {
        Specification<SurveillanceCase> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (stockCode != null) {
                predicates.add(builder.equal(root.get("stockCode"), stockCode));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("lastDetectedAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("lastDetectedAt"), to));
            }
            if (minimumScore != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("score"), minimumScore));
            }
            if (maximumScore != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("score"), maximumScore));
            }
            if (ruleType != null || severity != null) {
                Subquery<Long> signalQuery = query.subquery(Long.class);
                Root<AnomalyRecord> signal = signalQuery.from(AnomalyRecord.class);
                List<Predicate> signalPredicates = new ArrayList<>();
                signalPredicates.add(builder.equal(signal.get("caseId"), root.get("id")));
                if (ruleType != null) {
                    signalPredicates.add(builder.equal(signal.get("ruleType"), ruleType));
                }
                if (severity != null) {
                    signalPredicates.add(builder.equal(signal.get("severity"), severity));
                }
                signalQuery.select(signal.get("caseId"))
                        .where(signalPredicates.toArray(Predicate[]::new));
                predicates.add(builder.exists(signalQuery));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        return caseRepository.findAll(specification, pageable)
                .map(value -> CaseSummaryView.from(value, resolvedName(value)));
    }

    public CaseDetailView detail(Long id) {
        SurveillanceCase value = caseRepository.findById(id)
                .orElseThrow(() -> new CaseNotFoundException(id));
        List<AnomalyRecord> records = anomalyRepository.findByCaseIdOrderByDetectedAtAsc(id);
        String stockName = resolvedName(value);
        CompositeScore score = scoreCalculator.calculate(records.stream().map(AnomalyRecord::toDomain).toList());
        List<AnomalyView> signals = records.stream()
                .map(record -> AnomalyView.from(record, stockName)).toList();
        return new CaseDetailView(value.getId(), value.getStockCode(), stockName,
                value.getTitle(), value.getSummary(), value.getScore(), value.getAttentionLevel(),
                value.getScoreExplanation(), score.contributions(), score.simultaneousSignalBonus(),
                value.getStatus(), value.getSignalCount(), value.getFirstDetectedAt(), value.getLastDetectedAt(),
                value.getClosedAt(), value.getReviewer(), value.getClosureReason(), value.getClosureDetail(),
                value.getContextTags(), value.getVersion(), signals,
                noteRepository.findByCaseIdOrderByCreatedAtAsc(id).stream().map(CaseNoteView::from).toList(),
                historyRepository.findByCaseIdOrderByChangedAtAsc(id).stream().map(CaseHistoryView::from).toList(),
                AnomalyEvidence.DEFAULT_CAUTION);
    }

    private String resolvedName(SurveillanceCase value) {
        String storedName = value.getStockName();
        if (storedName != null && !storedName.isBlank() && !storedName.equals(value.getStockCode())) {
            return storedName;
        }
        String currentName = stockNameResolver.nameOf(value.getStockCode());
        return currentName == null || currentName.isBlank() ? value.getStockCode() : currentName;
    }
}
