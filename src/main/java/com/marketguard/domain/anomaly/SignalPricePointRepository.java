package com.marketguard.domain.anomaly;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SignalPricePointRepository extends JpaRepository<SignalPricePoint, SignalPricePointId> {

    /**
     * [from, to) 사이 종목 신호의 가격 경로를 (신호 시각, 신호, 시점) 순으로, 그 키가 after 뒤인 것부터 limit개.
     * 가격 점이 하나도 없는 신호도 시점이 빈 한 줄로 내보낸다. 분석이 "그날 첫 신호"를 고를 때 기록이
     * 빠진 신호를 건너뛰고 뒤 신호로 대체하지 않게 하기 위해서다. 시점이 없는 줄의 키 시점은 0으로 본다.
     * 내보내기가 전체를 메모리에 올리지 않도록 마지막 행의 키를 넘겨 이어서 읽는다(keyset).
     */
    @Query("""
            SELECT new com.marketguard.domain.anomaly.PricePathRow(
                a.id, a.detectedAt, a.stockCode, a.ruleType, a.severity, a.direction,
                a.observedValue, a.thresholdValue, a.deviationRatio,
                p.offsetMinutes, p.basePrice, p.price, p.marketChangePercent, p.sectorChangePercent)
            FROM AnomalyRecord a LEFT JOIN SignalPricePoint p ON p.anomalyId = a.id
            WHERE a.detectedAt >= :from AND a.detectedAt < :to
              AND a.stockCode NOT IN ('KOSPI', 'KOSDAQ')
              AND (a.detectedAt > :afterAt
                   OR (a.detectedAt = :afterAt AND (a.id > :afterId
                       OR (a.id = :afterId AND COALESCE(p.offsetMinutes, 0) > :afterOffset))))
            ORDER BY a.detectedAt, a.id, COALESCE(p.offsetMinutes, 0)
            """)
    List<PricePathRow> findPathRowsAfter(@Param("from") Instant from, @Param("to") Instant to,
                                         @Param("afterAt") Instant afterAt, @Param("afterId") long afterId,
                                         @Param("afterOffset") int afterOffset, Limit limit);
}
