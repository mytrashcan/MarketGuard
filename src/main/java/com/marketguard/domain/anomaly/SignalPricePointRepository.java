package com.marketguard.domain.anomaly;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalPricePointRepository extends JpaRepository<SignalPricePoint, SignalPricePointId> {

    /** [from, to) 사이 신호의 가격 경로를 신호 시각, 신호, 시점 순으로. */
    @Query("""
            SELECT new com.marketguard.domain.anomaly.PricePathRow(
                a.id, a.detectedAt, a.stockCode, a.ruleType, a.severity, a.direction,
                a.observedValue, a.thresholdValue, a.deviationRatio,
                p.offsetMinutes, p.basePrice, p.price, p.marketChangePercent, p.sectorChangePercent)
            FROM SignalPricePoint p JOIN AnomalyRecord a ON a.id = p.anomalyId
            WHERE a.detectedAt >= :from AND a.detectedAt < :to
            ORDER BY a.detectedAt, a.id, p.offsetMinutes
            """)
    List<PricePathRow> findPathRows(@Param("from") Instant from, @Param("to") Instant to);
}
