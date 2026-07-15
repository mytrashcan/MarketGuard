package com.marketguard.application;

import com.marketguard.detection.model.Anomaly;
import com.marketguard.domain.anomaly.AnomalyCooldownRepository;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional application service joining detection results to durable output ports. */
@Service
public class AnomalyRecordingService {

    private final AnomalyCooldownRepository cooldownRepository;
    private final AnomalyRepository anomalyRepository;

    public AnomalyRecordingService(
            AnomalyCooldownRepository cooldownRepository, AnomalyRepository anomalyRepository) {
        this.cooldownRepository = cooldownRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional
    public Optional<AnomalyRecord> recordIfEligible(Anomaly anomaly, Duration cooldown) {
        if (cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        int acquired = cooldownRepository.acquire(
                anomaly.stockCode(),
                anomaly.ruleType().name(),
                anomaly.detectedAt(),
                anomaly.detectedAt().minus(cooldown));
        if (acquired == 0) {
            return Optional.empty();
        }
        return Optional.of(anomalyRepository.save(AnomalyRecord.from(anomaly)));
    }
}
