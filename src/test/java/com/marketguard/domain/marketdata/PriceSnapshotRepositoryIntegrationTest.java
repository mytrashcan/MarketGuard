package com.marketguard.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL(Testcontainers)에 대해 리포지토리 쿼리(조회·정리 삭제)를 검증하는 통합 테스트.
 * Docker가 없으면 자동으로 건너뛴다(disabledWithoutDocker) — CI/로컬 어디서든 빌드가 깨지지 않게.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PriceSnapshotRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PriceSnapshotRepository repository;

    @Test
    void savesQueriesAndPrunesOnRealPostgres() {
        repository.save(new PriceSnapshot("005930", new BigDecimal("70000"), Instant.now().minusSeconds(7200)));
        repository.save(new PriceSnapshot("005930", new BigDecimal("71000"), Instant.now()));

        assertThat(repository.findByStockCodeOrderByCapturedAtDesc("005930", Limit.of(10))).hasSize(2);

        long deleted = repository.deleteByCapturedAtBefore(Instant.now().minusSeconds(3600));

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findByStockCodeOrderByCapturedAtDesc("005930", Limit.of(10))).hasSize(1);
    }
}
