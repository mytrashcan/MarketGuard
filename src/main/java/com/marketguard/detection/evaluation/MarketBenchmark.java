package com.marketguard.detection.evaluation;

import com.marketguard.detection.model.DecimalMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 같은 구간 스캔 종목들의 변동률 중앙값을 시장 기준으로 쓴다.
 * 지수(KOSPI 등)가 아니라 동일 가중 중앙값이므로 대형주 쏠림을 반영하지 않는다.
 */
public final class MarketBenchmark {

    private final int minimumSamples;

    public MarketBenchmark(int minimumSamples) {
        if (minimumSamples < 1) {
            throw new IllegalArgumentException("minimumSamples must be positive");
        }
        this.minimumSamples = minimumSamples;
    }

    /**
     * @param basePrices    종목별 구간 시작 가격
     * @param outcomePrices 종목별 구간 끝 가격
     * @return 두 가격이 모두 있는 종목의 변동률 중앙값. 그런 종목이 minimumSamples보다 적으면 null
     */
    public BigDecimal medianChangePercent(Map<String, BigDecimal> basePrices, Map<String, BigDecimal> outcomePrices) {
        List<BigDecimal> changes = new ArrayList<>();
        basePrices.forEach((code, base) -> {
            BigDecimal outcome = outcomePrices.get(code);
            if (base != null && outcome != null && base.signum() > 0 && outcome.signum() > 0) {
                changes.add(DecimalMath.percentageChange(outcome, base));
            }
        });
        if (changes.size() < minimumSamples) {
            return null;
        }
        changes.sort(null);
        int middle = changes.size() / 2;
        return changes.size() % 2 == 1 ? changes.get(middle)
                : changes.get(middle - 1).add(changes.get(middle))
                        .divide(BigDecimal.valueOf(2), DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
    }
}
