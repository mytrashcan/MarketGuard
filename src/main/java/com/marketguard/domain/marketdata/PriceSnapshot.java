package com.marketguard.domain.marketdata;

import com.marketguard.detection.model.MarketPrice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 특정 시점에 수집한 종목 시세 스냅샷.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "price_snapshot",
        indexes = @Index(name = "idx_snapshot_code_time", columnList = "stockCode, capturedAt")
)
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String stockCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private Instant capturedAt;

    public PriceSnapshot(String stockCode, BigDecimal price, Instant capturedAt) {
        this(new MarketPrice(stockCode, price, capturedAt));
    }

    private PriceSnapshot(MarketPrice marketPrice) {
        this.stockCode = marketPrice.stockCode();
        this.price = marketPrice.price();
        this.capturedAt = marketPrice.capturedAt();
    }

    public static PriceSnapshot from(MarketPrice marketPrice) {
        return new PriceSnapshot(marketPrice);
    }

    public MarketPrice toDomain() {
        return new MarketPrice(stockCode, price, capturedAt);
    }
}
