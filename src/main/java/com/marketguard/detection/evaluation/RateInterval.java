package com.marketguard.detection.evaluation;

import com.marketguard.detection.model.DecimalMath;
import java.math.BigDecimal;

/**
 * 비율(%)과 95% Wilson 신뢰구간. 표본이 작을수록 구간이 넓어져, 우연(50%)과 구분되는지 판단할 수 있다.
 *
 * @param ratePercent  관측 비율
 * @param lowerPercent 95% 신뢰구간 하한
 * @param upperPercent 95% 신뢰구간 상한
 * @param samples      표본 수
 */
public record RateInterval(BigDecimal ratePercent, BigDecimal lowerPercent, BigDecimal upperPercent, long samples) {

    private static final double Z_95 = 1.959963984540054;

    /** successes/samples의 비율과 신뢰구간. 표본이 없으면 null. */
    public static RateInterval of(long successes, long samples) {
        if (samples <= 0) {
            return null;
        }
        if (successes < 0 || successes > samples) {
            throw new IllegalArgumentException("successes must be between 0 and samples");
        }
        double n = samples;
        double p = successes / n;
        double z2 = Z_95 * Z_95;
        double denominator = 1 + z2 / n;
        double center = (p + z2 / (2 * n)) / denominator;
        double half = Z_95 * Math.sqrt(p * (1 - p) / n + z2 / (4 * n * n)) / denominator;
        return new RateInterval(percent(p), percent(Math.max(0, center - half)), percent(Math.min(1, center + half)),
                samples);
    }

    /** 신뢰구간 전체가 50%보다 위면 1, 아래면 -1, 50%를 포함하면 0. */
    public int comparedWithChance() {
        BigDecimal half = BigDecimal.valueOf(50);
        if (lowerPercent.compareTo(half) > 0) {
            return 1;
        }
        if (upperPercent.compareTo(half) < 0) {
            return -1;
        }
        return 0;
    }

    private static BigDecimal percent(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(DecimalMath.DISPLAY_SCALE, DecimalMath.ROUNDING_MODE);
    }
}
