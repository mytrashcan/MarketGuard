package com.marketguard.detection.baseline;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

public record IntradayObservation(LocalDate tradingDate, LocalTime time, BigDecimal value) {
    public IntradayObservation {
        Objects.requireNonNull(tradingDate, "tradingDate must not be null");
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("value must not be negative");
        }
    }
}
