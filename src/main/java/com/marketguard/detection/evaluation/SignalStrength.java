package com.marketguard.detection.evaluation;

import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;

/**
 * 신호가 임계값을 몇 배로 넘었는지(임계 배수)를 규칙과 무관하게 같은 뜻으로 맞춘다.
 *
 * <p>저장된 deviationRatio는 규칙마다 뜻이 다르다.
 * <ul>
 *   <li>가격 급변동·호가 불균형·가격·거래량 동시 급증: 이미 "관측값 ÷ 임계값"이다.</li>
 *   <li>거래량 급증: "평균 대비 배수"이고 임계값은 요구 배수라 둘을 나눈다.</li>
 *   <li>기관 수급: 임계값이 금액이라 |관측 금액| ÷ 임계 금액으로 계산한다.</li>
 *   <li>가격제한폭: "제한가까지 남은 거리"(작을수록 강함)라 같은 척도로 바꿀 수 없어 제외한다.</li>
 * </ul>
 */
public final class SignalStrength {

    private static final BigDecimal MID = new BigDecimal("1.5");
    private static final BigDecimal STRONG = new BigDecimal("2");

    public enum Band {
        /** 임계 1~1.5배 */
        NEAR,
        /** 임계 1.5~2배 */
        MID,
        /** 임계 2배 이상 */
        STRONG
    }

    private SignalStrength() {
    }

    /** 임계 배수. 계산할 수 없는 규칙이나 값이면 null. */
    public static BigDecimal thresholdMultiple(RuleType ruleType, BigDecimal observedValue,
                                               BigDecimal thresholdValue, BigDecimal deviationRatio) {
        if (ruleType == null) {
            return null;
        }
        return switch (ruleType) {
            case PRICE_SPIKE, ORDERBOOK_IMBALANCE, PRICE_VOLUME_SURGE ->
                    deviationRatio == null || deviationRatio.signum() < 0 ? null : deviationRatio;
            case VOLUME_SURGE -> divide(deviationRatio, thresholdValue);
            case INSTITUTIONAL_NET_BUY_SURGE, INSTITUTIONAL_NET_SELL_SURGE ->
                    divide(observedValue == null ? null : observedValue.abs(), thresholdValue);
            case PRICE_LIMIT, INVESTMENT_WARNING -> null;
        };
    }

    private static BigDecimal divide(BigDecimal value, BigDecimal threshold) {
        if (value == null || value.signum() < 0 || threshold == null || threshold.signum() <= 0) {
            return null;
        }
        return value.divide(threshold, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
    }

    /** 임계 배수 구간. null이면 null. 임계 미만 값(이론상 없음)은 NEAR로 본다. */
    public static Band band(BigDecimal thresholdMultiple) {
        if (thresholdMultiple == null) {
            return null;
        }
        if (thresholdMultiple.compareTo(STRONG) >= 0) {
            return Band.STRONG;
        }
        return thresholdMultiple.compareTo(MID) >= 0 ? Band.MID : Band.NEAR;
    }
}
