package com.marketguard.domain.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** 오래된 스냅샷 정리용. 삭제 건수를 반환. */
    long deleteByCapturedAtBefore(Instant cutoff);
}
