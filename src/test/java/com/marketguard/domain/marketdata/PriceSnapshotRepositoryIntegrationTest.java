package com.marketguard.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyCooldownRepository;
import com.marketguard.domain.anomaly.AnomalyRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL(Testcontainers)에 대해 리포지토리 쿼리(조회·정리 삭제)를 검증하는 통합 테스트.
 * Docker가 없으면 실패한다. 운영 DB 계약을 검증하지 않은 빌드는 성공으로 취급하지 않는다.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Testcontainers
class PriceSnapshotRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PriceSnapshotRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AnomalyRecordingService anomalyRecordingService;

    @Autowired
    AnomalyRepository anomalyRepository;

    @Autowired
    AnomalyCooldownRepository cooldownRepository;

    @BeforeEach
    void cleanDatabase() {
        anomalyRepository.deleteAll();
        cooldownRepository.deleteAll();
        repository.deleteAll();
    }

    @Test
    @Transactional
    void savesQueriesAndPrunesOnRealPostgres() {
        repository.save(new PriceSnapshot("005930", new BigDecimal("70000"), Instant.now().minusSeconds(7200)));
        repository.save(new PriceSnapshot("005930", new BigDecimal("71000"), Instant.now()));

        assertThat(repository.findByStockCodeOrderByCapturedAtDesc("005930", Limit.of(10))).hasSize(2);

        long deleted = repository.deleteByCapturedAtBefore(Instant.now().minusSeconds(3600));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findByStockCodeOrderByCapturedAtDesc("005930", Limit.of(10))).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'anomaly_cooldown'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void cooldownPersistsAcrossTransactionsAndAllowsTheExactBoundary() {
        Duration cooldown = Duration.ofMinutes(10);
        Instant firstAt = Instant.parse("2026-07-15T00:00:00Z");

        assertThat(anomalyRecordingService.recordIfEligible(anomaly(firstAt), cooldown)).isPresent();
        assertThat(anomalyRecordingService.recordIfEligible(anomaly(firstAt.plusSeconds(599)), cooldown)).isEmpty();
        assertThat(anomalyRecordingService.recordIfEligible(anomaly(firstAt.plusSeconds(600)), cooldown)).isPresent();

        assertThat(anomalyRepository.count()).isEqualTo(2);
    }

    @Test
    void concurrentSignalsAcquireCooldownOnlyOnce() throws Exception {
        Instant detectedAt = Instant.parse("2026-07-15T00:00:00Z");
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            ready.countDown();
            start.await();
            return anomalyRecordingService.recordIfEligible(anomaly(detectedAt), Duration.ofMinutes(10)).isPresent();
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = List.of(executor.submit(attempt), executor.submit(attempt));
            ready.await();
            start.countDown();

            assertThat(results).extracting(Future::get).containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }
        assertThat(anomalyRepository.count()).isEqualTo(1);
    }

    private static Anomaly anomaly(Instant detectedAt) {
        return new Anomaly("005930", RuleType.PRICE_SPIKE, Severity.WARNING, "test anomaly", detectedAt);
    }
}
