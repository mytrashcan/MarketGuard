package com.marketguard.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;

/** 스냅샷 정리는 행 수와 관계없이 DELETE 한 문장으로 끝나야 한다(전 종목 스캔 시 시간당 수십만 행). */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PriceSnapshotCleanupTest {

    private static final Instant CUTOFF = Instant.parse("2026-07-31T01:00:00Z");

    @Autowired
    private PriceSnapshotRepository repository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void deletesOnlyOlderSnapshotsInOneStatement() {
        for (int i = 1; i <= 50; i++) {
            repository.save(new PriceSnapshot("005930", new BigDecimal("70000"), CUTOFF.minusSeconds(i)));
        }
        repository.save(new PriceSnapshot("005930", new BigDecimal("70100"), CUTOFF));
        repository.save(new PriceSnapshot("005930", new BigDecimal("70200"), CUTOFF.plusSeconds(1)));
        repository.flush();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        int deleted = repository.deleteCapturedBefore(CUTOFF);

        assertThat(deleted).isEqualTo(50);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(statistics.getEntityDeleteCount()).isZero();
        assertThat(repository.findByStockCodeOrderByCapturedAtDesc("005930", Limit.of(10)))
                .extracting(PriceSnapshot::getCapturedAt)
                .containsExactly(CUTOFF.plusSeconds(1), CUTOFF);
    }
}
