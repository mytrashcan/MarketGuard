package com.marketguard.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "toss.http")
@Validated
public record TossHttpProperties(
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(1) @Max(5) int maxAttempts,
        @NotNull Duration initialBackoff,
        @NotNull Duration maxBackoff,
        @DecimalMin("0.0") @DecimalMax("1.0") double jitterFactor,
        @NotNull Duration maxRetryAfter) {

    public TossHttpProperties {
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
        requirePositive(initialBackoff, "initialBackoff");
        requirePositive(maxBackoff, "maxBackoff");
        requirePositive(maxRetryAfter, "maxRetryAfter");
        if (initialBackoff.compareTo(maxBackoff) > 0) {
            throw new IllegalArgumentException("initialBackoff must not exceed maxBackoff");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
