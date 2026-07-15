package com.marketguard.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "maintenance")
@Validated
public record MaintenanceProperties(
        @NotNull Duration snapshotCleanupInterval,
        @NotNull Duration snapshotRetention
) {
    public MaintenanceProperties {
        if (snapshotCleanupInterval != null && snapshotCleanupInterval.compareTo(Duration.ofSeconds(1)) < 0) {
            throw new IllegalArgumentException("snapshot cleanup interval must be at least one second");
        }
        if (snapshotRetention != null && snapshotRetention.compareTo(Duration.ofMinutes(1)) < 0) {
            throw new IllegalArgumentException("snapshot retention must be at least one minute");
        }
    }
}
