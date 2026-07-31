package com.marketguard.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Market-wide KRX institutional-flow anomaly thresholds. */
@ConfigurationProperties(prefix = "detection.institutional-flow")
@Validated
public record InstitutionalFlowProperties(
        @DecimalMin(value = "1.0", inclusive = false) BigDecimal multiplier,
        @Min(2) @Max(99) int lookback,
        @Min(2) @Max(99) int minimumSamples,
        @DecimalMin(value = "0", inclusive = false) BigDecimal minimumNetAmount,
        @NotNull Duration maxAge,
        @NotNull Duration cooldown
) {
    public InstitutionalFlowProperties {
        if (minimumSamples > lookback) {
            throw new IllegalArgumentException("minimumSamples must not exceed lookback");
        }
        if (maxAge != null && (maxAge.isZero() || maxAge.isNegative())) {
            throw new IllegalArgumentException("maxAge must be positive");
        }
        if (cooldown != null && (cooldown.isZero() || cooldown.isNegative())) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
    }
}
