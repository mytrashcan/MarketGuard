package com.marketguard.domain.anomaly;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyRepository extends JpaRepository<AnomalyRecord, Long> {

    List<AnomalyRecord> findByOrderByDetectedAtDesc(Limit limit);

    List<AnomalyRecord> findByCaseIdOrderByDetectedAtAsc(Long caseId);

    /** 결과 측정 대기 중인 신호 중 오래된 것부터. */
    List<AnomalyRecord> findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(Instant cutoff);

    @Query("""
            SELECT new com.marketguard.domain.anomaly.SignalStatRow(
                a.ruleType, a.severity, a.stockCode, a.detectedAt, a.outcomeStatus, a.outcomeBasePrice,
                a.outcomePrice, a.outcomeChangePercent, a.outcomeFollowedDirection,
                a.outcomeMarketChangePercent, a.outcomeExcessChangePercent, a.outcomeFollowedAfterMarket,
                a.outcomeHorizonMinutes)
            FROM AnomalyRecord a
            WHERE a.detectedAt >= :from AND a.detectedAt < :to
            """)
    List<SignalStatRow> findStatRows(@Param("from") Instant from, @Param("to") Instant to);
}
