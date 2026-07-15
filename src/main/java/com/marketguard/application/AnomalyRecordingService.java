package com.marketguard.application;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.application.port.StockNameResolver;
import com.marketguard.config.SurveillanceProperties;
import com.marketguard.detection.casework.CaseGroupingDecision;
import com.marketguard.detection.casework.CaseGroupingPolicy;
import com.marketguard.detection.casework.CaseScoreCalculator;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.CompositeScore;
import com.marketguard.detection.casework.ExistingCase;
import com.marketguard.domain.anomaly.AnomalyCooldownRepository;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.casework.CaseGroupLockRepository;
import com.marketguard.domain.casework.CaseStatusHistory;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional application service joining detection results to durable output ports. */
@Service
public class AnomalyRecordingService {

    private final AnomalyCooldownRepository cooldownRepository;
    private final AnomalyRepository anomalyRepository;
    private final SurveillanceCaseRepository caseRepository;
    private final CaseStatusHistoryRepository historyRepository;
    private final CaseGroupLockRepository lockRepository;
    private final StockNameResolver stockNameResolver;
    private final CaseGroupingPolicy groupingPolicy;
    private final CaseScoreCalculator scoreCalculator;
    private final CaseObservability observability;
    private final Clock clock;

    public AnomalyRecordingService(
            AnomalyCooldownRepository cooldownRepository,
            AnomalyRepository anomalyRepository,
            SurveillanceCaseRepository caseRepository,
            CaseStatusHistoryRepository historyRepository,
            CaseGroupLockRepository lockRepository,
            StockNameResolver stockNameResolver,
            SurveillanceProperties properties,
            CaseObservability observability,
            Clock clock) {
        this.cooldownRepository = cooldownRepository;
        this.anomalyRepository = anomalyRepository;
        this.caseRepository = caseRepository;
        this.historyRepository = historyRepository;
        this.lockRepository = lockRepository;
        this.stockNameResolver = stockNameResolver;
        this.groupingPolicy = new CaseGroupingPolicy(
                properties.groupingWindow(), properties.reactivationWindow());
        this.scoreCalculator = new CaseScoreCalculator(properties.scoreConfiguration());
        this.observability = observability;
        this.clock = clock;
    }

    @Transactional
    public Optional<AnomalyRecord> recordIfEligible(Anomaly anomaly, Duration cooldown) {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        int acquired = cooldownRepository.acquire(
                anomaly.stockCode(),
                anomaly.ruleType().name(),
                anomaly.detectedAt(),
                anomaly.detectedAt().minus(cooldown));
        if (acquired == 0) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        lockRepository.upsert(anomaly.stockCode(), now);
        lockRepository.lock(anomaly.stockCode())
                .orElseThrow(() -> new IllegalStateException("case grouping lock was not created"));

        SurveillanceCase latest = caseRepository
                .findFirstByStockCodeOrderByLastDetectedAtDesc(anomaly.stockCode()).orElse(null);
        ExistingCase existing = latest == null ? null
                : new ExistingCase(latest.getStatus(), latest.getLastDetectedAt(), latest.getClosedAt());
        CaseGroupingDecision decision = groupingPolicy.decide(existing, anomaly.detectedAt());
        String stockName = stockNameResolver.nameOf(anomaly.stockCode());
        List<Anomaly> signals = latest == null || decision == CaseGroupingDecision.CREATE
                ? new ArrayList<>()
                : new ArrayList<>(anomalyRepository.findByCaseIdOrderByDetectedAtAsc(latest.getId())
                        .stream().map(AnomalyRecord::toDomain).toList());
        signals.add(anomaly);
        CompositeScore score = scoreCalculator.calculate(signals);

        SurveillanceCase target;
        if (decision == CaseGroupingDecision.MERGE) {
            target = latest;
            target.merge(anomaly, stockName, score, now);
            observability.merged();
        } else if (decision == CaseGroupingDecision.REACTIVATE) {
            target = latest;
            CaseStatus previous = target.getStatus();
            target.reactivate(anomaly, stockName, score, now);
            historyRepository.save(new CaseStatusHistory(target.getId(), previous, CaseStatus.NEW,
                    "system", null, "최근 종결 사건에 새 신호가 발생해 재활성화", now));
            observability.reactivated();
        } else {
            if (latest != null && !latest.getStatus().isTerminal()) {
                CaseStatus previous = latest.getStatus();
                latest.closeForInactivity(now, "사건 그룹화 시간 창을 초과한 새 신호로 이전 사건 종결");
                historyRepository.save(new CaseStatusHistory(latest.getId(), previous, CaseStatus.CLOSED,
                        "system", latest.getClosureReason(), latest.getClosureDetail(), now));
            }
            target = caseRepository.saveAndFlush(SurveillanceCase.create(anomaly, stockName, score, now));
            historyRepository.save(new CaseStatusHistory(target.getId(), null, CaseStatus.NEW,
                    "system", null, "첫 이상징후로 사건 생성", now));
            observability.created();
        }
        caseRepository.save(target);
        AnomalyRecord saved = anomalyRepository.save(AnomalyRecord.from(anomaly, stockName, target.getId()));
        observability.recorded(anomaly.ruleType());
        return Optional.of(saved);
    }
}
