package com.marketguard.domain.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 신호 후 offsetMinutes 시점의 가격과 같은 구간의 시장·업종 변동률. 스냅샷이 지워진 뒤에도 남는 영구 기록이다.
 * 가격이 비어 있으면 그 시점의 스냅샷을 찾지 못했다는 뜻이다.
 */
@Entity
@Table(name = "signal_price_point")
@IdClass(SignalPricePointId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SignalPricePoint implements Persistable<SignalPricePointId> {

    @Id
    @Column(nullable = false)
    private Long anomalyId;

    @Id
    @Column(nullable = false)
    private Integer offsetMinutes;

    @Column(precision = 19, scale = 4)
    private BigDecimal basePrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(precision = 30, scale = 8)
    private BigDecimal marketChangePercent;

    @Column(precision = 30, scale = 8)
    private BigDecimal sectorChangePercent;

    @Column(nullable = false)
    private Instant recordedAt;

    /** 키를 직접 정하므로, 새 행임을 알려 save()가 저장 전 SELECT(merge)를 하지 않게 한다. */
    @Transient
    private boolean fresh = true;

    public SignalPricePoint(Long anomalyId, int offsetMinutes, BigDecimal basePrice, BigDecimal price,
                            BigDecimal marketChangePercent, BigDecimal sectorChangePercent, Instant recordedAt) {
        this.anomalyId = anomalyId;
        this.offsetMinutes = offsetMinutes;
        this.basePrice = basePrice;
        this.price = price;
        this.marketChangePercent = marketChangePercent;
        this.sectorChangePercent = sectorChangePercent;
        this.recordedAt = recordedAt;
    }

    @Override
    public SignalPricePointId getId() {
        return new SignalPricePointId(anomalyId, offsetMinutes);
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }
}
