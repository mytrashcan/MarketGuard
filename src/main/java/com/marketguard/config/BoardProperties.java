package com.marketguard.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "board")
@Validated
public record BoardProperties(
        @Min(1000) long pushIntervalMs,
        @Min(1000) long detailRefreshMs
) {
}
