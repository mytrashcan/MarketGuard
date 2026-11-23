package com.marketguard.domain.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByOrderByCreatedAtDesc(Limit limit);

    /** cutoff보다 오래된 감사 기록을 한 번에 지운다. 지운 건수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoff")
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
