package com.marketguard.collector.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.config.CollectorProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CollectorObservabilityTest {

    @Test
    void publishesBoundedMetricsAndHealthWithoutSecrets() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CollectorObservability observability = new CollectorObservability(
                registry,
                new CollectorProperties(true, 30_000),
                Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC));

        Timer.Sample sample = observability.startScan();
        observability.recordProcessedPrices(3, false);
        observability.recordBatchFailure();
        observability.recordItemFailure();
        observability.recordAnomaly();
        observability.finishScan(sample, "success");

        assertThat(registry.get("marketguard.collector.scan.duration").tag("outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("marketguard.collector.prices.processed").tag("scan", "broad").counter().count())
                .isEqualTo(3);
        assertThat(registry.get("marketguard.collector.batch.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("marketguard.collector.item.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("marketguard.anomalies.recorded").counter().count()).isEqualTo(1);
        assertThat(observability.health().getDetails())
                .containsEntry("enabled", true)
                .containsEntry("lastOutcome", "success")
                .doesNotContainKeys("clientId", "clientSecret", "token");
    }
}
