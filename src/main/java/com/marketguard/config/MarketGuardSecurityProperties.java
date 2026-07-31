package com.marketguard.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Security boundary for the operator-only dashboard and APIs. */
@ConfigurationProperties(prefix = "marketguard.security")
@Validated
public record MarketGuardSecurityProperties(
        boolean enabled,
        String username,
        String password,
        String operatorToken,
        List<String> allowedOrigins,
        @Min(1) @Max(10_000) int requestsPerMinute
) {
    public MarketGuardSecurityProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
