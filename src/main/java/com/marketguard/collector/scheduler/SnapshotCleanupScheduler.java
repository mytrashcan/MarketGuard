package com.marketguard.collector.scheduler;

import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.config.MaintenanceProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전 종목 스캔으로 빠르게 쌓이는 시세 스냅샷을 주기적으로 정리한다(메모리·DB 보호).
 * 룰은 최근 일부 스냅샷만 보므로 오래된 데이터는 삭제해도 된다.
 */
@Slf4j
@Component
public class SnapshotCleanupScheduler {

    private final PriceSnapshotRepository snapshotRepository;
    private final MaintenanceProperties properties;
    private final Clock clock;

    public SnapshotCleanupScheduler(PriceSnapshotRepository snapshotRepository,
                                    MaintenanceProperties properties,
                                    Clock clock) {
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${maintenance.snapshot-cleanup-interval}")
    @Transactional
    public void prune() {
        Instant cutoff = clock.instant().minus(properties.snapshotRetention());
        long deleted = snapshotRepository.deleteByCapturedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("오래된 시세 스냅샷 {}건 정리", deleted);
        }
    }
}
