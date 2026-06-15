package com.marketguard.domain.marketdata;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    List<PriceSnapshot> findByStockCodeOrderByCapturedAtDesc(String stockCode, Limit limit);
}
