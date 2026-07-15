package com.marketguard.application;

import com.marketguard.config.SurveillanceProperties;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.domain.casework.CaseStatusHistory;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CaseLifecycleScheduler {

    private final SurveillanceCaseRepository caseRepository;
    private final CaseStatusHistoryRepository historyRepository;
    private final SurveillanceProperties properties;
    private final CaseObservability observability;
    private final Clock clock;

    public CaseLifecycleScheduler(SurveillanceCaseRepository caseRepository,
                                  CaseStatusHistoryRepository historyRepository,
                                  SurveillanceProperties properties,
                                  CaseObservability observability,
                                  Clock clock) {
        this.caseRepository = caseRepository;
        this.historyRepository = historyRepository;
        this.properties = properties;
        this.observability = observability;
        this.clock = clock;
    }

    @Transactional
    @Scheduled(fixedDelayString = "${surveillance.lifecycle-interval:PT1M}")
    public void closeInactiveCases() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.inactivityTimeout());
        for (SurveillanceCase value : caseRepository
                .findTop200ByStatusInAndLastDetectedAtBeforeOrderByLastDetectedAtAsc(
                EnumSet.of(CaseStatus.NEW, CaseStatus.REVIEWING, CaseStatus.WATCHING,
                        CaseStatus.ESCALATED), cutoff)) {
            CaseStatus previous = value.getStatus();
            value.closeForInactivity(now, "설정된 비활성 시간 동안 새 신호가 없어 자동 종결");
            historyRepository.save(new CaseStatusHistory(value.getId(), previous, CaseStatus.CLOSED,
                    "system", value.getClosureReason(), value.getClosureDetail(), now));
            observability.transitioned(CaseStatus.CLOSED);
        }
    }
}
