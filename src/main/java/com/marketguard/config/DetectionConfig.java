package com.marketguard.config;

import com.marketguard.detection.engine.RuleEngine;
import com.marketguard.detection.engine.RuleEvaluationObserver;
import com.marketguard.detection.rule.DetectionRule;
import com.marketguard.detection.rule.InvestmentWarningRule;
import com.marketguard.detection.rule.InstitutionalFlowDetector;
import com.marketguard.detection.rule.OrderbookImbalanceRule;
import com.marketguard.detection.rule.PriceLimitRule;
import com.marketguard.detection.rule.PriceSpikeRule;
import com.marketguard.detection.rule.PriceVolumeSurgeRule;
import com.marketguard.detection.rule.VolumeSurgeRule;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composes the framework-free detection core from validated external configuration. */
@Configuration
public class DetectionConfig {

    @Bean
    DetectionRule priceSpikeRule(PriceSpikeProperties properties) {
        return new PriceSpikeRule(properties.thresholdPercent(), properties.lookback(), properties.maxAge());
    }

    @Bean
    DetectionRule priceLimitRule(PriceLimitProperties properties) {
        return new PriceLimitRule(properties.proximityPercent());
    }

    @Bean
    DetectionRule orderbookImbalanceRule(OrderbookImbalanceProperties properties) {
        return new OrderbookImbalanceRule(properties.ratioThreshold());
    }

    @Bean
    DetectionRule volumeSurgeRule(VolumeSurgeProperties properties) {
        return new VolumeSurgeRule(properties.multiplier(), properties.lookback());
    }

    @Bean
    DetectionRule investmentWarningRule() {
        return new InvestmentWarningRule();
    }

    @Bean
    DetectionRule priceVolumeSurgeRule(
            PriceSpikeProperties price, VolumeSurgeProperties volume) {
        return new PriceVolumeSurgeRule(price.thresholdPercent(), price.lookback(),
                volume.multiplier(), volume.lookback(), price.maxAge());
    }

    @Bean
    InstitutionalFlowDetector institutionalFlowDetector(InstitutionalFlowProperties properties) {
        return new InstitutionalFlowDetector(
                properties.multiplier(),
                properties.lookback(),
                properties.minimumSamples(),
                properties.minimumNetAmount(),
                properties.maxAge());
    }

    @Bean
    RuleEngine ruleEngine(List<DetectionRule> rules, RuleEvaluationObserver observer) {
        return new RuleEngine(rules, observer);
    }
}
