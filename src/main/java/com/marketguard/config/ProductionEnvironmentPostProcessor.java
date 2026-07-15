package com.marketguard.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import org.springframework.util.PlaceholderResolutionException;

/** Validates required production values before DataSource, Flyway, or security beans are created. */
public final class ProductionEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }
        requireResolved(environment, "spring.datasource.url", "SPRING_DATASOURCE_URL");
        requireResolved(environment, "spring.datasource.username", "SPRING_DATASOURCE_USERNAME");
        requireResolved(environment, "spring.datasource.password", "SPRING_DATASOURCE_PASSWORD");
        requireResolved(environment, "marketguard.security.username", "MARKETGUARD_ADMIN_USERNAME");
        requireResolved(environment, "marketguard.security.password", "MARKETGUARD_ADMIN_PASSWORD");
        requireResolved(environment, "marketguard.security.allowed-origins", "MARKETGUARD_ALLOWED_ORIGINS");

        if (environment.getProperty("collector.enabled", Boolean.class, false)) {
            requireResolved(environment, "toss.client-id", "TOSS_CLIENT_ID");
            requireResolved(environment, "toss.client-secret", "TOSS_CLIENT_SECRET");
        }
    }

    @Override
    public int getOrder() {
        return ConfigDataEnvironmentPostProcessor.ORDER + 1;
    }

    private static void requireResolved(ConfigurableEnvironment environment, String property, String variable) {
        String value;
        try {
            value = environment.getProperty(property);
        } catch (PlaceholderResolutionException exception) {
            throw new IllegalStateException(variable + " is required in the prod profile", exception);
        }
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException(variable + " is required in the prod profile");
        }
    }
}
