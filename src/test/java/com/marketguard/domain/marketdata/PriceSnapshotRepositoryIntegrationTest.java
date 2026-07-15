package com.marketguard.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.application.CaseReviewService;
import com.marketguard.application.StaleCaseVersionException;
import com.marketguard.dashboard.CaseQueryService;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyCooldownRepository;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.casework.CaseGroupLockRepository;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import com.marketguard.domain.casework.CaseNoteRepository;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    SurveillanceCaseRepository caseRepository;

    @Autowired
    CaseGroupLockRepository caseGroupLockRepository;

    @Autowired
    CaseReviewService caseReviewService;

    @Autowired
    CaseNoteRepository caseNoteRepository;

    @Autowired
    CaseStatusHistoryRepository caseStatusHistoryRepository;

    @Autowired
    CaseQueryService caseQueryService;

    @BeforeEach
    void cleanDatabase() {
        anomalyRepository.deleteAll();
        caseRepository.deleteAll();
        caseGroupLockRepository.deleteAll();
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
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'anomaly_cooldown'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'surveillance_case'",
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
        assertThat(caseRepository.count()).isEqualTo(1);
        assertThat(anomalyRepository.findAll()).allSatisfy(record -> {
            assertThat(record.getCaseId()).isNotNull();
            assertThat(record.getTitle()).isNotBlank();
            assertThat(record.getCaution()).contains("판단할 수 없습니다");
        });
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

    @Test
    void reviewWorkflowPersistsStatusNotesHistoryAndRejectsStaleVersions() {
        anomalyRecordingService.recordIfEligible(
                anomaly(Instant.parse("2026-07-15T00:00:00Z")), Duration.ofMinutes(10));
        var surveillanceCase = caseRepository.findAll().get(0);

        var updated = caseReviewService.updateStatus(surveillanceCase.getId(), surveillanceCase.getVersion(),
                CaseStatus.REVIEWING, null, null, "operator");
        caseReviewService.addNote(updated.getId(), "공개 공시 확인 필요", "operator");

        assertThat(caseRepository.findById(updated.getId()).orElseThrow().getStatus())
                .isEqualTo(CaseStatus.REVIEWING);
        assertThat(caseNoteRepository.findByCaseIdOrderByCreatedAtAsc(updated.getId()))
                .singleElement().extracting(com.marketguard.domain.casework.CaseNote::getAuthor)
                .isEqualTo("operator");
        assertThat(caseStatusHistoryRepository.findByCaseIdOrderByChangedAtAsc(updated.getId()))
                .extracting(com.marketguard.domain.casework.CaseStatusHistory::getToStatus)
                .containsExactly(CaseStatus.NEW, CaseStatus.REVIEWING);
        assertThat(caseRepository.analyzeRules()).singleElement().satisfies(analytics -> {
            assertThat(analytics.getRuleType()).isEqualTo("PRICE_SPIKE");
            assertThat(analytics.getOccurrenceCount()).isEqualTo(1);
            assertThat(analytics.getCaseCount()).isEqualTo(1);
        });
        assertThatThrownBy(() -> caseReviewService.updateStatus(updated.getId(), 0,
                CaseStatus.WATCHING, null, null, "operator"))
                .isInstanceOf(StaleCaseVersionException.class);
    }

    @Test
    void groupsDistinctRulesAndSupportsFilteredPagedQueriesAndDatabaseConstraints() {
        Instant firstAt = Instant.parse("2026-07-15T00:00:00Z");
        anomalyRecordingService.recordIfEligible(
                anomaly("005930", RuleType.PRICE_SPIKE, firstAt), Duration.ofMinutes(10));
        anomalyRecordingService.recordIfEligible(
                anomaly("005930", RuleType.VOLUME_SURGE, firstAt.plusSeconds(60)), Duration.ofMinutes(10));
        anomalyRecordingService.recordIfEligible(
                anomaly("000660", RuleType.PRICE_SPIKE, firstAt.plusSeconds(120)), Duration.ofMinutes(10));

        var grouped = caseRepository.findFirstByStockCodeOrderByLastDetectedAtDesc("005930").orElseThrow();
        assertThat(grouped.getSignalCount()).isEqualTo(2);
        assertThat(grouped.getScore()).isEqualTo(60);
        assertThat(anomalyRepository.findByCaseIdOrderByDetectedAtAsc(grouped.getId())).hasSize(2);

        var filtered = caseQueryService.find(CaseStatus.NEW, RuleType.VOLUME_SURGE, Severity.WARNING,
                "005930", firstAt, firstAt.plusSeconds(180), 50, 70,
                PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "lastDetectedAt")));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent()).singleElement().satisfies(value -> {
            assertThat(value.stockCode()).isEqualTo("005930");
            assertThat(value.score()).isEqualTo(60);
        });

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update surveillance_case set score = 101 where id = ?", grouped.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static Anomaly anomaly(Instant detectedAt) {
        return anomaly("005930", RuleType.PRICE_SPIKE, detectedAt);
    }

    private static Anomaly anomaly(String stockCode, RuleType ruleType, Instant detectedAt) {
        return new Anomaly(stockCode, ruleType, Severity.WARNING, "test anomaly", detectedAt);
    }
}
