package com.marketguard.domain.anomaly;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalPricePointRepository extends JpaRepository<SignalPricePoint, SignalPricePointId> {

    /**
     * [from, to) 사이 신호의 가격 경로를 (신호 시각, 신호, 시점) 순으로, 그 키가 after 뒤인 것부터 limit개.
     * 내보내기가 전체를 메모리에 올리지 않도록 마지막 행의 키를 넘겨 이어서 읽는다(keyset).
     */
    @Query("""
            SELECT new com.marketguard.domain.anomaly.PricePathRow(
                a.id, a.detectedAt, a.stockCode, a.ruleType, a.severity, a.direction,
                a.observedValue, a.thresholdValue, a.deviationRatio,
                p.offsetMinutes, p.basePrice, p.price, p.marketChangePercent, p.sectorChangePercent)
            FROM SignalPricePoint p JOIN AnomalyRecord a ON a.id = p.anomalyId
            WHERE a.detectedAt >= :from AND a.detectedAt < :to
              AND (a.detectedAt > :afterAt
                   OR (a.detectedAt = :afterAt AND (a.id > :afterId
                       OR (a.id = :afterId AND p.offsetMinutes > :afterOffset))))
            ORDER BY a.detectedAt, a.id, p.offsetMinutes
            """)
    List<PricePathRow> findPathRowsAfter(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("afterAt") Instant afterAt, @Param("afterId") long afterId,
                                         @Param("afterOffset") int afterOffset, Limit limit);
}
