package com.marketguard.detection.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DecimalMathTest {

    @Test
    void calculatesPercentagesWithTheSharedDecimalPolicy() {
        assertThat(DecimalMath.percentageChange(new BigDecimal("100.01"), new BigDecimal("100")))
                .isEqualByComparingTo("0.01000000");
        assertThat(DecimalMath.average(List.of(
                new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("2"))))
                .isEqualByComparingTo("1.66666667");
    }

    @Test
    void rejectsInvalidMarketPrices() {
        assertThatThrownBy(() -> new MarketPrice("../bad", BigDecimal.ONE, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarketPrice("005930", BigDecimal.ZERO, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
