package com.marketguard.detection.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RateIntervalTest {

    @Test
    void matchesTheWilsonIntervalForAKnownCase() {
        RateInterval interval = RateInterval.of(60, 100);

        assertThat(interval.ratePercent()).isEqualByComparingTo("60.00");
        assertThat(interval.lowerPercent()).isEqualByComparingTo("50.20");
        assertThat(interval.upperPercent()).isEqualByComparingTo("69.06");
    }

    @Test
    void separatesARealEdgeFromChanceOnlyWithEnoughSamples() {
        assertThat(RateInterval.of(6, 10).comparedWithChance()).isZero();
        assertThat(RateInterval.of(60, 100).comparedWithChance()).isEqualTo(1);
        assertThat(RateInterval.of(35, 100).comparedWithChance()).isEqualTo(-1);
    }

    @Test
    void staysInsideZeroToOneHundredAndRejectsImpossibleCounts() {
        RateInterval all = RateInterval.of(5, 5);

        assertThat(all.upperPercent()).isEqualByComparingTo("100.00");
        assertThat(all.lowerPercent()).isGreaterThan(java.math.BigDecimal.ZERO);
        assertThat(RateInterval.of(0, 0)).isNull();
        assertThatThrownBy(() -> RateInterval.of(6, 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
