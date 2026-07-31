package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionSafetyValidatorTest {

    private static final TossApiProperties VALID_TOSS = new TossApiProperties(
            "https://openapi.tossinvest.com", "https://openapi.tossinvest.com/oauth2/token",
            "client", "secret", List.of("005930"));

    @Test
    void collectorFailsFastWithoutCredentials() {
        ProductionSafetyValidator validator = validator(new MockEnvironment(), disabledSecurity(),
                new CollectorProperties(true, 30_000),
                new TossApiProperties(VALID_TOSS.baseUrl(), VALID_TOSS.authUrl(), "", "", List.of()));

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOSS_CLIENT_ID");
    }

    @Test
    void productionAllowsExplicitlyDisabledAuthentication() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(validator(environment, disabledSecurity(), disabledCollector(), VALID_TOSS)::validate)
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWildcardOrigins() {
        MarketGuardSecurityProperties security =
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", "", List.of("*"), 120);

        assertThatThrownBy(validator(new MockEnvironment(), security, disabledCollector(), VALID_TOSS)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Wildcard");
    }

    @Test
    void acceptsSecureProductionConfiguration() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        MarketGuardSecurityProperties security = new MarketGuardSecurityProperties(
                true, "operator", "a-secure-password", "", List.of("https://marketguard.example"), 120);

        assertThatCode(validator(environment, security, disabledCollector(), VALID_TOSS)::validate)
                .doesNotThrowAnyException();
    }

    private static ProductionSafetyValidator validator(MockEnvironment environment,
                                                        MarketGuardSecurityProperties security,
                                                        CollectorProperties collector,
                                                        TossApiProperties toss) {
        return new ProductionSafetyValidator(environment, security, collector, toss);
    }

    private static MarketGuardSecurityProperties disabledSecurity() {
        return new MarketGuardSecurityProperties(false, "", "", "", List.of(), 120);
    }

    private static CollectorProperties disabledCollector() {
        return new CollectorProperties(false, 30_000);
    }
}
