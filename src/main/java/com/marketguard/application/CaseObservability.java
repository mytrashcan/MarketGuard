package com.marketguard.application;

import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.model.RuleType;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CaseObservability {

    private final Counter casesCreated;
    private final Counter casesMerged;
    private final Counter casesReactivated;
    private final Counter notesCreated;
    private final Map<RuleType, Counter> detections = new EnumMap<>(RuleType.class);
    private final Map<CaseStatus, Counter> transitions = new EnumMap<>(CaseStatus.class);

    public CaseObservability(MeterRegistry registry, SurveillanceCaseRepository repository) {
        casesCreated = counter(registry, "marketguard.cases.created", "Created surveillance cases");
        casesMerged = counter(registry, "marketguard.cases.merged", "Signals merged into cases");
        casesReactivated = counter(registry, "marketguard.cases.reactivated", "Reactivated cases");
        notesCreated = counter(registry, "marketguard.cases.notes.created", "Created case notes");
        for (RuleType type : RuleType.values()) {
            detections.put(type, Counter.builder("marketguard.detections.by.rule")
                    .description("Recorded detections by rule")
                    .tag("rule", type.name()).register(registry));
        }
        for (CaseStatus status : CaseStatus.values()) {
            transitions.put(status, Counter.builder("marketguard.cases.status.transitions")
                    .description("Case status transitions")
                    .tag("target", status.name()).register(registry));
            Gauge.builder("marketguard.cases.by.status", repository,
                            value -> value.countByStatus(status))
                    .description("Current case count by status")
                    .tag("status", status.name()).register(registry);
        }
        registerScoreGauge(registry, repository, "0_29", 0, 29);
        registerScoreGauge(registry, repository, "30_59", 30, 59);
        registerScoreGauge(registry, repository, "60_100", 60, 100);
    }

    public void recorded(RuleType ruleType) {
        detections.get(ruleType).increment();
    }

    public void created() {
        casesCreated.increment();
    }

    public void merged() {
        casesMerged.increment();
    }

    public void reactivated() {
        casesReactivated.increment();
    }

    public void transitioned(CaseStatus target) {
        transitions.get(target).increment();
    }

    public void noteCreated() {
        notesCreated.increment();
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    private static void registerScoreGauge(MeterRegistry registry, SurveillanceCaseRepository repository,
                                           String bucket, int minimum, int maximum) {
        Gauge.builder("marketguard.cases.by.score", repository,
                        value -> value.countByScoreBetween(minimum, maximum))
                .description("Current case count by score bucket")
                .tag("bucket", bucket).register(registry);
    }
}
