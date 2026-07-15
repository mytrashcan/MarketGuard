package com.marketguard.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "detection")
@Validated
public record DetectionRuntimeProperties(@Min(1000) long cooldownMs) {
}
