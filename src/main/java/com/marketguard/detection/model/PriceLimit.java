package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 가격제한폭(상·하한가) 값 객체.
 */
public record PriceLimit(BigDecimal upperLimit, BigDecimal lowerLimit) {
    public PriceLimit {
        Objects.requireNonNull(upperLimit, "upperLimit must not be null");
        Objects.requireNonNull(lowerLimit, "lowerLimit must not be null");
        if (upperLimit.signum() <= 0 || lowerLimit.signum() <= 0) {
            throw new IllegalArgumentException("price limits must be positive");
        }
        if (lowerLimit.compareTo(upperLimit) > 0) {
            throw new IllegalArgumentException("lowerLimit must not exceed upperLimit");
        }
    }
}
