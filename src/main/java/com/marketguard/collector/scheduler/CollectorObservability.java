package com.marketguard.collector.scheduler;

import com.marketguard.config.CollectorProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Bounded collector metrics and credential-free operational health details. */
@Component
public class CollectorObservability implements HealthIndicator {

    private final MeterRegistry registry;
    private final CollectorProperties properties;
    private final Clock clock;
    private final Counter batchFailures;
    private final Counter itemFailures;
    private final Counter anomaliesRecorded;
    private final AtomicReference<Instant> lastStartedAt = new AtomicReference<>();
    private final AtomicReference<Instant> lastCompletedAt = new AtomicReference<>();
    private final AtomicReference<String> lastOutcome = new AtomicReference<>("not_started");

    public CollectorObservability(MeterRegistry registry, CollectorProperties properties, Clock clock) {
        this.registry = registry;
        this.properties = properties;
        this.clock = clock;
        this.batchFailures = Counter.builder("marketguard.collector.batch.failures")
                .description("Failed upstream market-price batches")
                .register(registry);
        this.itemFailures = Counter.builder("marketguard.collector.item.failures")
                .description("Failed per-symbol detection pipelines")
                .register(registry);
        this.anomaliesRecorded = Counter.builder("marketguard.anomalies.recorded")
                .description("Anomalies durably recorded after cooldown")
                .register(registry);
    }

    Timer.Sample startScan() {
        lastStartedAt.set(clock.instant());
        return Timer.start(registry);
    }

    void finishScan(Timer.Sample sample, String outcome) {
        sample.stop(Timer.builder("marketguard.collector.scan.duration")
                .description("End-to-end collector scan duration")
                .tag("outcome", outcome)
                .register(registry));
        lastOutcome.set(outcome);
        lastCompletedAt.set(clock.instant());
    }

    void recordBatchFailure() {
        batchFailures.increment();
    }

    void recordItemFailure() {
        itemFailures.increment();
    }

    void recordAnomaly() {
        anomaliesRecorded.increment();
    }

    void recordProcessedPrices(int count, boolean deep) {
        Counter.builder("marketguard.collector.prices.processed")
                .description("Market prices evaluated by the rule engine")
                .tag("scan", deep ? "deep" : "broad")
                .register(registry)
                .increment(count);
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up()
                .withDetail("enabled", properties.enabled())
                .withDetail("lastOutcome", lastOutcome.get());
        Instant started = lastStartedAt.get();
        Instant completed = lastCompletedAt.get();
        if (started != null) {
            builder.withDetail("lastStartedAt", started);
        }
        if (completed != null) {
            builder.withDetail("lastCompletedAt", completed);
        }
        return builder.build();
    }
}
