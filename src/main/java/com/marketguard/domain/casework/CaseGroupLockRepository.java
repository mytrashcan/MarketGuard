package com.marketguard.domain.casework;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CaseGroupLockRepository extends JpaRepository<CaseGroupLock, String> {

    @Modifying
    @Query(value = """
            INSERT INTO case_group_lock (stock_code, updated_at)
            VALUES (:stockCode, :updatedAt)
            ON CONFLICT (stock_code) DO UPDATE SET updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("stockCode") String stockCode, @Param("updatedAt") Instant updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lock from CaseGroupLock lock where lock.stockCode = :stockCode")
    Optional<CaseGroupLock> lock(@Param("stockCode") String stockCode);
}
