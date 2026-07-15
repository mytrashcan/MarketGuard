package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ProductionEnvironmentPostProcessorTest {

    private final ProductionEnvironmentPostProcessor postProcessor = new ProductionEnvironmentPostProcessor();

    @Test
    void ignoresNonProductionProfiles() {
        assertThatCode(() -> postProcessor.postProcessEnvironment(new MockEnvironment(), application()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnresolvedProductionSecretBeforeContextCreation() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.withProperty("spring.datasource.url", "${SPRING_DATASOURCE_URL}");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, application()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("SPRING_DATASOURCE_URL is required in the prod profile");
    }

    @Test
    void acceptsCompleteProductionSettingsWithCollectorDisabled() {
        MockEnvironment environment = completeEnvironment();

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, application()))
                .doesNotThrowAnyException();
    }

    @Test
    void additionallyRequiresTossCredentialsForAnEnabledCollector() {
        MockEnvironment environment = completeEnvironment();
        environment.withProperty("collector.enabled", "true");

        assertThatThrownBy(() -> postProcessor.postProcessEnvironment(environment, application()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TOSS_CLIENT_ID is required in the prod profile");
    }

    private static MockEnvironment completeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.withProperty("spring.datasource.url", "jdbc:postgresql://db/marketguard")
                .withProperty("spring.datasource.username", "marketguard")
                .withProperty("spring.datasource.password", "database-password")
                .withProperty("marketguard.security.username", "operator")
                .withProperty("marketguard.security.password", "a-secure-password")
                .withProperty("marketguard.security.allowed-origins", "https://marketguard.example")
                .withProperty("collector.enabled", "false");
        return environment;
    }

    private static SpringApplication application() {
        return new SpringApplication();
    }
}
