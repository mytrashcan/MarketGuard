package com.marketguard.domain.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AnomalyCountRepositoryTest {

    private static final Instant SINCE = Instant.parse("2026-07-30T15:00:00Z");

    @Autowired
    private AnomalyRepository anomalies;

    @Autowired
    private AnomalyCountRepository counts;

    @Test
    void countsAllAndCriticalSignalsFromTheSameRows() {
        anomalies.save(signal(Severity.CRITICAL, SINCE.minusSeconds(1)));
        anomalies.save(signal(Severity.CRITICAL, SINCE));
        anomalies.save(signal(Severity.WARNING, SINCE.plusSeconds(60)));
        anomalies.save(signal(Severity.WARNING, SINCE.plusSeconds(120)));

        assertThat(counts.countSince(SINCE)).isEqualTo(new SignalCount(3, 1));
    }

    @Test
    void returnsZerosRatherThanNullOnAnEmptyDay() {
        assertThat(counts.countSince(SINCE)).isEqualTo(new SignalCount(0, 0));
    }

    private static AnomalyRecord signal(Severity severity, Instant at) {
        return new AnomalyRecord("005930", RuleType.PRICE_SPIKE, severity, "가격 급변동", at);
    }
}
