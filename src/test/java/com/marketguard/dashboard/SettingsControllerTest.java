package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.config.DetectionRuntimeProperties;
import com.marketguard.config.InstitutionalFlowProperties;
import com.marketguard.config.OrderbookImbalanceProperties;
import com.marketguard.config.PriceLimitProperties;
import com.marketguard.config.PriceSpikeProperties;
import com.marketguard.config.SurveillanceProperties;
import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SettingsControllerTest {

    private final SettingsController controller = new SettingsController(
            new DetectionRuntimeProperties(600_000),
            new PriceSpikeProperties(new BigDecimal("3.0"), 20, Duration.ofMinutes(30)),
            new PriceLimitProperties(new BigDecimal("1.0")),
            new OrderbookImbalanceProperties(new BigDecimal("3.0")),
            new VolumeSurgeProperties(new BigDecimal("3.0"), 20),
            new InstitutionalFlowProperties(new BigDecimal("3"), 20, 5, new BigDecimal("100000000000"),
                    Duration.ofMinutes(30), Duration.ofHours(6)),
            new SurveillanceProperties(Duration.ofMinutes(10), Duration.ofMinutes(20), Duration.ofMinutes(30),
                    Map.of(RuleType.PRICE_SPIKE, 25, RuleType.VOLUME_SURGE, 25), 10, 100, 30, 60));

    @Test
    void exposesEveryRuleWithItsConfiguredThresholds() {
        DetectionSettingsView view = controller.detection();

        assertThat(view.cooldownMinutes()).isEqualByComparingTo("10");
        assertThat(view.rules()).extracting(DetectionSettingsView.Rule::ruleType)
                .containsExactlyInAnyOrder(RuleType.values());
        DetectionSettingsView.Rule spike = rule(view, RuleType.PRICE_SPIKE);
        assertThat(spike.parameters()).extracting(DetectionSettingsView.Parameter::key)
                .containsExactly("thresholdPercent", "priceLookback", "maxAgeMinutes");
        assertThat(spike.parameters().get(0).value()).isEqualByComparingTo("3.0");
        assertThat(spike.parameters().get(2).value()).isEqualByComparingTo("30");
        assertThat(rule(view, RuleType.INSTITUTIONAL_NET_BUY_SURGE).parameters().get(1).value())
                .isEqualByComparingTo("1000");
        assertThat(rule(view, RuleType.INVESTMENT_WARNING).parameters()).isEmpty();
        // 기관 수급은 공통 알림 간격(10분)이 아니라 자체 재알림 간격(6시간)을 쓴다.
        assertThat(rule(view, RuleType.INSTITUTIONAL_NET_SELL_SURGE).parameters())
                .filteredOn(parameter -> parameter.key().equals("reAlertMinutes"))
                .singleElement().satisfies(parameter -> assertThat(parameter.value()).isEqualByComparingTo("360"));
    }

    @Test
    void reportsCaseGroupingAndEveryRuleWeight() {
        DetectionSettingsView.Cases cases = controller.detection().cases();

        assertThat(cases.groupingWindowMinutes()).isEqualByComparingTo("10");
        assertThat(cases.inactivityTimeoutMinutes()).isEqualByComparingTo("20");
        assertThat(cases.highThreshold()).isEqualTo(60);
        assertThat(cases.ruleWeights()).hasSize(RuleType.values().length)
                .contains(new DetectionSettingsView.Weight(RuleType.PRICE_SPIKE, 25),
                        new DetectionSettingsView.Weight(RuleType.PRICE_LIMIT, 0));
    }

    @Test
    void keepsSecondsInsteadOfTruncatingToWholeMinutes() {
        assertThat(SettingsController.minutes(Duration.ofSeconds(30))).isEqualByComparingTo("0.5");
        assertThat(SettingsController.minutes(Duration.ofSeconds(90))).isEqualByComparingTo("1.5");
        assertThat(SettingsController.minutes(Duration.ofMinutes(30))).isEqualByComparingTo("30");
        assertThat(SettingsController.minutes(Duration.ofMillis(1_000))).isEqualByComparingTo("0.016667");
    }

    private static DetectionSettingsView.Rule rule(DetectionSettingsView view, RuleType type) {
        return view.rules().stream().filter(rule -> rule.ruleType() == type).findFirst().orElseThrow();
    }
}
