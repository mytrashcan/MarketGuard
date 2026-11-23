package com.marketguard.detection.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SignalStrengthTest {

    @Test
    void normalizesEachRuleToAMultipleOfItsThreshold() {
        // 가격 급변동: deviationRatio가 이미 |변동률| ÷ 임계값
        assertThat(SignalStrength.thresholdMultiple(RuleType.PRICE_SPIKE,
                new BigDecimal("4.5"), new BigDecimal("3"), new BigDecimal("1.5"))).isEqualByComparingTo("1.5");
        // 거래량 급증: 평균의 4.5배, 요구 배수 3배 → 임계 1.5배
        assertThat(SignalStrength.thresholdMultiple(RuleType.VOLUME_SURGE,
                new BigDecimal("4500"), new BigDecimal("3"), new BigDecimal("4.5"))).isEqualByComparingTo("1.5");
        // 기관 순매도: |-3,000억| ÷ 임계 1,000억 → 3배
        assertThat(SignalStrength.thresholdMultiple(RuleType.INSTITUTIONAL_NET_SELL_SURGE,
                new BigDecimal("-300000000000"), new BigDecimal("100000000000"), new BigDecimal("6")))
                .isEqualByComparingTo("3");
    }

    @Test
    void leavesRulesWithoutAComparableStrengthEmpty() {
        assertThat(SignalStrength.thresholdMultiple(RuleType.PRICE_LIMIT,
                new BigDecimal("29900"), new BigDecimal("1"), new BigDecimal("0.3"))).isNull();
        assertThat(SignalStrength.thresholdMultiple(RuleType.VOLUME_SURGE,
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("4"))).isNull();
        assertThat(SignalStrength.thresholdMultiple(RuleType.PRICE_SPIKE, null, null, null)).isNull();
    }

    @Test
    void groupsMultiplesIntoBands() {
        assertThat(SignalStrength.band(new BigDecimal("1.2"))).isEqualTo(SignalStrength.Band.NEAR);
        assertThat(SignalStrength.band(new BigDecimal("1.5"))).isEqualTo(SignalStrength.Band.MID);
        assertThat(SignalStrength.band(new BigDecimal("2"))).isEqualTo(SignalStrength.Band.STRONG);
        assertThat(SignalStrength.band(null)).isNull();
    }
}
