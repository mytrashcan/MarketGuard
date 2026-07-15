package com.marketguard.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "scheduling")
@Validated
public record SchedulingProperties(@Min(1) @Max(16) int poolSize) {
}
