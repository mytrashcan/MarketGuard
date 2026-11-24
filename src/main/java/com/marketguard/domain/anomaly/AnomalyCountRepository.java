package com.marketguard.domain.anomaly;

import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** 화면 요약용 신호 개수. 행을 불러오지 않고 데이터베이스에서 센다. */
public interface AnomalyCountRepository extends Repository<AnomalyRecord, Long> {

    /** 전체와 심각 신호를 한 쿼리로 세어, 두 숫자가 같은 행 집합을 가리키게 한다. */
    @Query("""
            SELECT new com.marketguard.domain.anomaly.SignalCount(
                COUNT(a),
                COALESCE(SUM(CASE WHEN a.severity = com.marketguard.detection.model.Severity.CRITICAL
                                  THEN 1 ELSE 0 END), 0))
            FROM AnomalyRecord a
            WHERE a.detectedAt >= :since
            """)
    SignalCount countSince(@Param("since") Instant since);
}
