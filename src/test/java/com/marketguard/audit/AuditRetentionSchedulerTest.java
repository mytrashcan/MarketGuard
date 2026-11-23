package com.marketguard.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketguard.config.MaintenanceProperties;
import com.marketguard.domain.audit.AuditLogRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuditRetentionSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-07-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void keepsEverythingByDefault() {
        AuditRetentionScheduler scheduler = scheduler(null);

        assertThat(scheduler.prune()).isZero();
        verifyNoInteractions(repository, auditService);
    }

    @Test
    void deletesOlderRecordsAndAuditsTheDeletion() {
        Instant cutoff = NOW.minus(Duration.ofDays(365));
        when(repository.deleteCreatedBefore(cutoff)).thenReturn(42);

        assertThat(scheduler(Duration.ofDays(365)).prune()).isEqualTo(42);

        verify(auditService).record(AuditRetentionScheduler.ACTION,
                "deleted=42 before=" + cutoff, "SUCCESS", 0L);
    }

    @Test
    void rejectsRetentionShorterThanADay() {
        assertThatThrownBy(() -> new MaintenanceProperties(
                Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one day");
        assertThat(new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofHours(1), Duration.ZERO)
                .auditRetention()).isZero();
    }

    private AuditRetentionScheduler scheduler(Duration retention) {
        return new AuditRetentionScheduler(repository, auditService,
                new MaintenanceProperties(Duration.ofMinutes(10), Duration.ofHours(1), retention), CLOCK);
    }
}
