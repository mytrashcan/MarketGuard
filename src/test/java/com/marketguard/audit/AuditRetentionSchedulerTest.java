package com.marketguard.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.config.MaintenanceProperties;
import com.marketguard.domain.audit.AuditLog;
import com.marketguard.domain.audit.AuditLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** 실제 JPA 저장소로 어떤 행이 지워지고 정리 기록이 어떻게 남는지 확인한다. */
@DataJpaTest
class AuditRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Duration RETENTION = Duration.ofDays(365);
    private static final Instant CUTOFF = NOW.minus(RETENTION);

    @Autowired
    private AuditLogRepository repository;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        repository.save(log("OLD", CUTOFF.minusSeconds(1)));
        repository.save(log("AT_CUTOFF", CUTOFF));
        repository.save(log("NEW", CUTOFF.plusSeconds(1)));
    }

    @Test
    void keepsEverythingByDefault() {
        assertThat(scheduler(null).prune()).isZero();
        assertThat(actions()).containsExactlyInAnyOrder("OLD", "AT_CUTOFF", "NEW");
    }

    @Test
    void deletesOnlyRecordsStrictlyBeforeTheCutoffAndRecordsTheCleanup() {
        assertThat(scheduler(RETENTION).prune()).isEqualTo(1);

        assertThat(actions()).containsExactlyInAnyOrder("AT_CUTOFF", "NEW", AuditRetentionScheduler.ACTION);
        AuditLog cleanup = repository.findAll().stream()
                .filter(entry -> entry.getAction().equals(AuditRetentionScheduler.ACTION))
                .findFirst().orElseThrow();
        assertThat(cleanup.getDetail()).isEqualTo("deleted=1 before=" + CUTOFF);
        assertThat(cleanup.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void writesNoCleanupRecordWhenNothingIsOldEnough() {
        repository.deleteAll();
        repository.save(log("NEW", NOW));

        assertThat(scheduler(RETENTION).prune()).isZero();
        assertThat(actions()).containsExactly("NEW");
    }

    @Test
    void rejectsRetentionShorterThanADay() {
        assertThatThrownBy(() -> new MaintenanceProperties(
                Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one day");
    }

    private AuditRetentionScheduler scheduler(Duration retention) {
        return new AuditRetentionScheduler(repository,
                new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofHours(1), retention),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private List<String> actions() {
        return repository.findAll().stream().map(AuditLog::getAction).toList();
    }

    private static AuditLog log(String action, Instant at) {
        return new AuditLog(action, "detail", "SUCCESS", 0L, at);
    }
}
