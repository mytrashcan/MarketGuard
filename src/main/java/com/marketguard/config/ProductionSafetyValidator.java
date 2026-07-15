package com.marketguard.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Fails startup when a data-collecting or production process has unsafe configuration. */
@Component
public class ProductionSafetyValidator {

    private static final int MINIMUM_PASSWORD_LENGTH = 16;
    private static final Pattern OPERATOR_USERNAME = Pattern.compile("[A-Za-z0-9._@-]{3,64}");

    private final Environment environment;
    private final MarketGuardSecurityProperties security;
    private final CollectorProperties collector;
    private final TossApiProperties toss;

    public ProductionSafetyValidator(Environment environment,
                                     MarketGuardSecurityProperties security,
                                     CollectorProperties collector,
                                     TossApiProperties toss) {
        this.environment = environment;
        this.security = security;
        this.collector = collector;
        this.toss = toss;
    }

    @PostConstruct
    void validate() {
        if (collector.enabled()) {
            requireText(toss.clientId(), "TOSS_CLIENT_ID is required when the collector is enabled");
            requireText(toss.clientSecret(), "TOSS_CLIENT_SECRET is required when the collector is enabled");
        }

        if (security.enabled()) {
            requireText(security.username(), "The operator username is required when security is enabled");
            requireText(security.password(), "The operator password is required when security is enabled");
            if (!OPERATOR_USERNAME.matcher(security.username()).matches()) {
                throw new IllegalStateException("The operator username has an invalid format");
            }
            if (security.password().length() < MINIMUM_PASSWORD_LENGTH) {
                throw new IllegalStateException("The operator password must contain at least 16 characters");
            }
            if (security.password().chars().anyMatch(Character::isISOControl)) {
                throw new IllegalStateException("The operator password must not contain control characters");
            }
            validateOrigins();
        }

        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            if (!security.enabled()) {
                throw new IllegalStateException("Operator authentication must be enabled in the prod profile");
            }
            requireHttps(toss.baseUrl(), "Toss market-data base URL");
            requireHttps(toss.authUrl(), "Toss authentication URL");
        }
    }

    private void validateOrigins() {
        if (security.allowedOrigins().isEmpty()) {
            throw new IllegalStateException("At least one trusted WebSocket origin is required");
        }
        for (String origin : security.allowedOrigins()) {
            requireText(origin, "Trusted WebSocket origins must not be blank");
            if (origin.contains("*")) {
                throw new IllegalStateException("Wildcard WebSocket origins are not allowed");
            }
            URI parsed = URI.create(origin);
            if (parsed.getScheme() == null || parsed.getHost() == null
                    || parsed.getUserInfo() != null || parsed.getRawQuery() != null || parsed.getRawFragment() != null
                    || (parsed.getRawPath() != null && !parsed.getRawPath().isEmpty())) {
                throw new IllegalStateException("Trusted WebSocket origins must be absolute origins");
            }
            boolean localHttp = parsed.getScheme().equalsIgnoreCase("http")
                    && (parsed.getHost().equalsIgnoreCase("localhost") || parsed.getHost().equals("127.0.0.1"));
            if (!parsed.getScheme().equalsIgnoreCase("https") && !localHttp) {
                throw new IllegalStateException("Non-local trusted origins must use HTTPS");
            }
        }
    }

    private static void requireHttps(String value, String name) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException(name + " must use HTTPS");
        }
    }

    private static void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(message);
        }
    }
}
