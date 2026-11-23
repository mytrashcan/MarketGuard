package com.marketguard.detection.evaluation;

import com.marketguard.detection.model.DecimalMath;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 같은 규칙에서 나온 신호들의 결과 요약.
 *
 * @param signals             기간 내 전체 신호 수
 * @param measured            결과를 측정한 신호 수
 * @param directional         측정된 신호 중 방향 판정이 가능한 신호 수(followedRate의 분모)
 * @param followedRatePercent 방향 판정 가능한 신호 중 신호 방향으로 움직인 비율. 표본이 없으면 null
 * @param medianAbsChangePercent 측정된 변동률 절대값의 중앙값. 표본이 없으면 null
 * @param adjustedDirectional 시장 기준을 뺀 초과 변동으로 방향 판정이 가능한 신호 수
 * @param adjustedFollowedRatePercent 초과 변동이 신호 방향과 같은 비율. 표본이 없으면 null
 */
public record OutcomeStatistics(
        long signals,
        long measured,
        long directional,
        BigDecimal followedRatePercent,
        BigDecimal medianAbsChangePercent,
        long adjustedDirectional,
        BigDecimal adjustedFollowedRatePercent
) {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");

    /** outcomes에는 기간 내 모든 신호의 결과를 넣는다. 아직 측정 전인 신호는 null로 넣는다. */
    public static OutcomeStatistics of(Collection<SignalOutcome> outcomes) {
        long signals = outcomes == null ? 0 : outcomes.size();
        List<SignalOutcome> measured = (outcomes == null ? List.<SignalOutcome>of() : outcomes).stream()
                .filter(Objects::nonNull)
                .filter(outcome -> outcome.status() == OutcomeStatus.MEASURED)
                .toList();
        List<Boolean> directional = measured.stream()
                .map(SignalOutcome::followedDirection)
                .filter(Objects::nonNull)
                .toList();
        List<Boolean> adjusted = measured.stream()
                .map(SignalOutcome::followedAfterMarket)
                .filter(Objects::nonNull)
                .toList();
        return new OutcomeStatistics(signals, measured.size(), directional.size(), rate(directional),
                median(measured.stream().map(outcome -> outcome.changePercent().abs()).toList()),
                adjusted.size(), rate(adjusted));
    }

    private static BigDecimal rate(List<Boolean> decisions) {
        if (decisions.isEmpty()) {
            return null;
        }
        return BigDecimal.valueOf(decisions.stream().filter(Boolean::booleanValue).count())
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(decisions.size()), DecimalMath.DISPLAY_SCALE, DecimalMath.ROUNDING_MODE);
    }

    static BigDecimal median(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        BigDecimal value = sorted.size() % 2 == 1 ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle)).divide(TWO, DecimalMath.CALCULATION_SCALE, DecimalMath.ROUNDING_MODE);
        return DecimalMath.display(value);
    }
}
