package com.marketguard.domain.anomaly;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyRepository extends JpaRepository<AnomalyRecord, Long> {

    List<AnomalyRecord> findByOrderByDetectedAtDesc(Limit limit);
}
