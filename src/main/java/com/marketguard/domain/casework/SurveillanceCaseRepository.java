package com.marketguard.domain.casework;

import com.marketguard.detection.casework.CaseStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface SurveillanceCaseRepository
        extends JpaRepository<SurveillanceCase, Long>, JpaSpecificationExecutor<SurveillanceCase> {

    Optional<SurveillanceCase> findFirstByStockCodeOrderByLastDetectedAtDesc(String stockCode);

    List<SurveillanceCase> findTop200ByStatusInAndLastDetectedAtBeforeOrderByLastDetectedAtAsc(
            Collection<CaseStatus> statuses, Instant cutoff);

    long countByStatus(CaseStatus status);

    long countByScoreBetween(int minimum, int maximum);

    @Query(value = """
            SELECT anomaly.rule_type AS ruleType,
                   COUNT(*) AS occurrenceCount,
                   COUNT(DISTINCT surveillance.id) AS caseCount,
                   COUNT(DISTINCT CASE WHEN surveillance.status = 'DISMISSED' THEN surveillance.id END)
                       AS dismissedCount,
                   COUNT(DISTINCT CASE WHEN surveillance.status = 'ESCALATED' THEN surveillance.id END)
                       AS escalatedCount,
                   AVG(CASE WHEN surveillance.status IN ('DISMISSED', 'CLOSED')
                       THEN EXTRACT(EPOCH FROM (surveillance.updated_at - surveillance.first_detected_at)) END)
                       AS averageReviewSeconds
            FROM anomaly_record anomaly
            JOIN surveillance_case surveillance ON surveillance.id = anomaly.case_id
            GROUP BY anomaly.rule_type
            ORDER BY occurrenceCount DESC, anomaly.rule_type ASC
            """, nativeQuery = true)
    List<RuleAnalyticsProjection> analyzeRules();
}
