package com.marketguard.domain.anomaly;

import com.marketguard.detection.model.RuleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "anomaly_cooldown")
@IdClass(AnomalyCooldownId.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnomalyCooldown {

    @Id
    @Column(nullable = false, length = 20)
    private String stockCode;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RuleType ruleType;

    @Column(nullable = false)
    private Instant lastEmittedAt;
}
