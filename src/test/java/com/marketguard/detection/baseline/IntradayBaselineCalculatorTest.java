package com.marketguard.detection.baseline;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntradayBaselineCalculatorTest {

    private final IntradayBaselineCalculator calculator = new IntradayBaselineCalculator(5, 3);

    @Test
    void calculatesSameTimeMeanMedianAndStandardDeviationWithoutFloatingPoint() {
        IntradayBaseline result = calculator.calculate(LocalTime.of(9, 7), List.of(
                observation(1, 9, 5, "100"), observation(2, 9, 6, "200"),
                observation(3, 9, 9, "300"), observation(4, 10, 0, "999")));

        assertThat(result.source()).isEqualTo(BaselineSource.SAME_TIME_BUCKET);
        assertThat(result.bucketStart()).isEqualTo(LocalTime.of(9, 5));
        assertThat(result.mean()).isEqualByComparingTo("200.00000000");
        assertThat(result.median()).isEqualByComparingTo("200");
        assertThat(result.standardDeviation()).isEqualByComparingTo("81.64965809");
    }

    @Test
    void fallsBackAndThenReportsInsufficientData() {
        IntradayBaseline fallback = calculator.calculate(LocalTime.of(9, 7), List.of(
                observation(1, 9, 5, "100"), observation(2, 10, 0, "200"),
                observation(3, 11, 0, "300")));
        IntradayBaseline insufficient = calculator.calculate(LocalTime.of(9, 7), List.of(
                observation(1, 9, 5, "100")));

        assertThat(fallback.source()).isEqualTo(BaselineSource.FALLBACK_ALL_SAMPLES);
        assertThat(insufficient.available()).isFalse();
        assertThat(insufficient.sampleCount()).isEqualTo(1);
    }

    private static IntradayObservation observation(int day, int hour, int minute, String value) {
        return new IntradayObservation(LocalDate.of(2026, 7, day), LocalTime.of(hour, minute),
                new BigDecimal(value));
    }
}
