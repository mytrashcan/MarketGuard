package com.marketguard.domain.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    List<PriceSnapshot> findByStockCodeOrderByCapturedAtDesc(String stockCode, Limit limit);

    /** 구간 안에서 가장 늦은 스냅샷(신호 시점 기준가). */
    Optional<PriceSnapshot> findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtDesc(
            String stockCode, Instant from, Instant to);

    /** 구간 안에서 가장 이른 스냅샷(측정 시점 가격). */
    Optional<PriceSnapshot> findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtAsc(
            String stockCode, Instant from, Instant to);

    /** 구간 안의 모든 종목 가격(시장 기준 계산용). 필요한 열만 읽는다. */
    @Query("""
            SELECT new com.marketguard.domain.marketdata.SnapshotPrice(s.stockCode, s.price, s.capturedAt)
            FROM PriceSnapshot s
            WHERE s.capturedAt >= :from AND s.capturedAt <= :to
            """)
    List<SnapshotPrice> findPricesBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * 오래된 스냅샷 정리용. 삭제 건수를 반환.
     * 파생 메서드(deleteBy…)는 행을 모두 엔티티로 읽은 뒤 한 건씩 지우므로 DELETE 한 문장으로 처리한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PriceSnapshot s WHERE s.capturedAt < :cutoff")
    int deleteCapturedBefore(@Param("cutoff") Instant cutoff);
}
