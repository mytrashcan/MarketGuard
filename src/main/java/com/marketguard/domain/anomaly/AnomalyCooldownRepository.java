package com.marketguard.domain.anomaly;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnomalyCooldownRepository extends JpaRepository<AnomalyCooldown, AnomalyCooldownId> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO anomaly_cooldown (stock_code, rule_type, last_emitted_at)
            VALUES (:stockCode, :ruleType, :detectedAt)
            ON CONFLICT (stock_code, rule_type) DO UPDATE
            SET last_emitted_at = EXCLUDED.last_emitted_at
            WHERE anomaly_cooldown.last_emitted_at <= :cutoff
            """, nativeQuery = true)
    int acquire(
            @Param("stockCode") String stockCode,
            @Param("ruleType") String ruleType,
            @Param("detectedAt") Instant detectedAt,
            @Param("cutoff") Instant cutoff);
}
