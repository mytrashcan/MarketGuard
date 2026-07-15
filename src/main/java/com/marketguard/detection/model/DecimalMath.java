package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Shared decimal policy for market ratios and percentages. */
public final class DecimalMath {

    public static final int CALCULATION_SCALE = 8;
    public static final int DISPLAY_SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private DecimalMath() {
    }

    public static BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), CALCULATION_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal percentageChange(BigDecimal current, BigDecimal baseline) {
        requirePositive(current, "current");
        requirePositive(baseline, "baseline");
        return current.subtract(baseline)
                .multiply(ONE_HUNDRED)
                .divide(baseline, CALCULATION_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal ratio(long numerator, long denominator) {
        if (numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("ratio values must be non-negative with a positive denominator");
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), CALCULATION_SCALE, ROUNDING_MODE);
    }

    public static BigDecimal display(BigDecimal value) {
        return value.setScale(DISPLAY_SCALE, ROUNDING_MODE);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
