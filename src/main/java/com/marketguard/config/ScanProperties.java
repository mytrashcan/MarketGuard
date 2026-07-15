package com.marketguard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scan")
public record ScanProperties(String symbolsFile, boolean marketHoursOnly) {
}
