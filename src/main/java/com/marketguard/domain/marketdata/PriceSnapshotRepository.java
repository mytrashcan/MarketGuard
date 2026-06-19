package com.marketguard.domain.marketdata;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    List<PriceSnapshot> findByStockCodeOrderByCapturedAtDesc(String stockCode, Limit limit);

    /** 오래된 스냅샷 정리용. 삭제 건수를 반환. */
    long deleteByCapturedAtBefore(Instant cutoff);
}
