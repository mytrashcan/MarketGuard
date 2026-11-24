package com.marketguard.domain.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SignalPricePointRepositoryTest {

    private static final Instant AT = Instant.parse("2026-07-31T01:00:00Z");

    @Autowired
    private AnomalyRepository anomalies;

    @Autowired
    private SignalPricePointRepository points;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void findsStockSignalsMissingThePointInTheWindow() {
        AnomalyRecord recorded = anomalies.save(signal("005930", AT));
        AnomalyRecord missing = anomalies.save(signal("000660", AT.plusSeconds(30)));
        anomalies.save(signal("KOSPI", AT.plusSeconds(40)));
        anomalies.save(signal("035720", AT.minusSeconds(1)));
        points.save(point(recorded.getId(), 5));

        List<AnomalyRecord> due = anomalies.findMissingPricePoint(AT.minusSeconds(1), AT.plusSeconds(60), 5, Limit.of(10));

        assertThat(due).extracting(AnomalyRecord::getId).containsExactly(missing.getId());
        // 다른 시점은 따로 센다
        assertThat(anomalies.findMissingPricePoint(AT.minusSeconds(1), AT.plusSeconds(60), 15, Limit.of(10)))
                .extracting(AnomalyRecord::getId).containsExactly(recorded.getId(), missing.getId());
    }

    @Test
    void insertsNewPointsWithoutReadingThemFirst() {
        AnomalyRecord signal = anomalies.save(signal("005930", AT));
        entityManager.flush();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        points.saveAll(List.of(point(signal.getId(), 5), point(signal.getId(), 15)));
        entityManager.flush();

        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void exportsEachPointWithItsSignal() {
        AnomalyRecord signal = anomalies.save(signal("005930", AT));
        anomalies.save(signal("000660", AT.plusSeconds(3600)));
        points.save(point(signal.getId(), 15));
        points.save(point(signal.getId(), 5));
        entityManager.flush();
        entityManager.clear();

        List<PricePathRow> rows = points.findPathRows(AT, AT.plusSeconds(60));

        assertThat(rows).extracting(PricePathRow::offsetMinutes).containsExactly(5, 15);
        assertThat(rows.get(0).stockCode()).isEqualTo("005930");
        assertThat(rows.get(0).ruleType()).isEqualTo(RuleType.PRICE_SPIKE);
        assertThat(rows.get(0).price()).isEqualByComparingTo("101");
    }

    private static SignalPricePoint point(Long anomalyId, int offset) {
        return new SignalPricePoint(anomalyId, offset, new BigDecimal("100"), new BigDecimal("101"),
                new BigDecimal("0.5"), null, AT.plusSeconds(offset * 60L));
    }

    private static AnomalyRecord signal(String code, Instant at) {
        return new AnomalyRecord(code, RuleType.PRICE_SPIKE, Severity.WARNING, "급변동", at);
    }
}
