package com.marketguard.dashboard;

import com.marketguard.detection.evaluation.RateInterval;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 기간별 신호 측정 결과.
 *
 * @param horizonMinutes 결과를 잰 시점(신호 후 분)
 * @param daily          KST 날짜별 신호 수. 신호가 없는 날도 0으로 포함한다.
 * @param rules          규칙별 신호 수와 사후 결과
 * @param coverage       결과 측정 상태별 신호 수
 */
public record SignalAnalyticsView(
        int days,
        LocalDate from,
        LocalDate to,
        long horizonMinutes,
        long totalSignals,
        List<Day> daily,
        List<Rule> rules,
        Coverage coverage
) {
    public record Day(LocalDate date, long signals, long critical, long stocks) {
    }

    /**
     * @param followed               방향 판정이 가능한 측정 신호 중 신호 방향으로 움직인 비율과 95% 신뢰구간.
     *                               50%가 우연 수준이다. 표본이 없으면 null
     * @param medianAbsChangePercent 측정된 변동률 절대값의 중앙값
     * @param followedAfterMarket    같은 구간 시장 기준 변동을 뺀 뒤의 같은 비율. 표본이 없으면 null
     */
    public record Rule(
            RuleType ruleType,
            long signals,
            long measured,
            RateInterval followed,
            BigDecimal medianAbsChangePercent,
            RateInterval followedAfterMarket
    ) {
    }

    /**
     * @param otherHorizon 현재와 다른 horizon으로 잰 결과. 섞지 않도록 규칙별 통계에서 제외한다.
     */
    public record Coverage(long measured, long noPriceData, long notApplicable, long pending, long otherHorizon) {
    }
}
