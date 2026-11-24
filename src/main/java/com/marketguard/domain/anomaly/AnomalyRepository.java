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

    /**
     * (from, to] 사이 종목 신호 중 offsetMinutes 시점 가격이 아직 기록되지 않은 것을 오래된 순으로.
     * 시장 전체 신호(KOSPI·KOSDAQ)는 종목 가격이 없으므로 뺀다.
     */
    @Query("""
            SELECT a FROM AnomalyRecord a
            WHERE a.detectedAt > :from AND a.detectedAt <= :to
              AND a.stockCode NOT IN ('KOSPI', 'KOSDAQ')
              AND NOT EXISTS (SELECT 1 FROM SignalPricePoint p
                              WHERE p.anomalyId = a.id AND p.offsetMinutes = :offset)
            ORDER BY a.detectedAt, a.id
            """)
    List<AnomalyRecord> findMissingPricePoint(@Param("from") Instant from, @Param("to") Instant to,
                                              @Param("offset") int offsetMinutes, Limit limit);

    @Query("""
            SELECT new com.marketguard.domain.anomaly.SignalStatRow(
                a.ruleType, a.severity, a.stockCode, a.detectedAt, a.outcomeStatus, a.outcomeBasePrice,
                a.outcomePrice, a.outcomeChangePercent, a.outcomeFollowedDirection,
                a.outcomeMarketChangePercent, a.outcomeExcessChangePercent, a.outcomeFollowedAfterMarket,
                a.outcomeHorizonMinutes, a.observedValue, a.thresholdValue, a.deviationRatio,
                a.outcomeSectorChangePercent, a.outcomeFollowedAfterSector)
            FROM AnomalyRecord a
            WHERE a.detectedAt >= :from AND a.detectedAt < :to
            """)
    List<SignalStatRow> findStatRows(@Param("from") Instant from, @Param("to") Instant to);
}
