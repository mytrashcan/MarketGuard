package com.marketguard.detection.baseline;

import com.marketguard.detection.model.DecimalMath;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;

/** 동일 장중 시간 버킷의 평균·중앙값·표준편차와 데이터 부족 폴백을 계산한다. */
public final class IntradayBaselineCalculator {

    private static final MathContext SQRT_CONTEXT = MathContext.DECIMAL64;
    private final int bucketMinutes;
    private final int minimumSamples;

    public IntradayBaselineCalculator(int bucketMinutes, int minimumSamples) {
        if (bucketMinutes < 1 || bucketMinutes > 60 || 60 % bucketMinutes != 0) {
            throw new IllegalArgumentException("bucketMinutes must divide 60 and be between 1 and 60");
        }
        if (minimumSamples < 2 || minimumSamples > 100) {
            throw new IllegalArgumentException("minimumSamples must be between 2 and 100");
        }
        this.bucketMinutes = bucketMinutes;
        this.minimumSamples = minimumSamples;
    }

    public IntradayBaseline calculate(LocalTime target, List<IntradayObservation> observations) {
        LocalTime bucket = bucketStart(target);
        List<BigDecimal> sameBucket = safe(observations).stream()
                .filter(observation -> bucketStart(observation.time()).equals(bucket))
                .map(IntradayObservation::value)
                .toList();
        if (sameBucket.size() >= minimumSamples) {
            return statistics(bucket, sameBucket, BaselineSource.SAME_TIME_BUCKET);
        }
        List<BigDecimal> fallback = safe(observations).stream().map(IntradayObservation::value).toList();
        if (fallback.size() >= minimumSamples) {
            return statistics(bucket, fallback, BaselineSource.FALLBACK_ALL_SAMPLES);
        }
        return new IntradayBaseline(bucket, null, null, null, fallback.size(),
                BaselineSource.INSUFFICIENT_DATA);
    }

    private IntradayBaseline statistics(LocalTime bucket, List<BigDecimal> values, BaselineSource source) {
        List<BigDecimal> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        BigDecimal mean = DecimalMath.average(sorted);
        BigDecimal median = sorted.size() % 2 == 1
                ? sorted.get(sorted.size() / 2)
                : sorted.get(sorted.size() / 2 - 1).add(sorted.get(sorted.size() / 2))
                        .divide(BigDecimal.valueOf(2), DecimalMath.CALCULATION_SCALE,
                                DecimalMath.ROUNDING_MODE);
        BigDecimal variance = sorted.stream()
                .map(value -> value.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(sorted.size()), DecimalMath.CALCULATION_SCALE,
                        DecimalMath.ROUNDING_MODE);
        BigDecimal standardDeviation = variance.sqrt(SQRT_CONTEXT)
                .setScale(DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
        return new IntradayBaseline(bucket, mean, median, standardDeviation, sorted.size(), source);
    }

    private LocalTime bucketStart(LocalTime value) {
        int minute = value.getMinute() - value.getMinute() % bucketMinutes;
        return value.withMinute(minute).withSecond(0).withNano(0);
    }

    private static List<IntradayObservation> safe(List<IntradayObservation> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
