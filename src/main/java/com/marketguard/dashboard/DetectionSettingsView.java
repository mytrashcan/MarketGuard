package com.marketguard.dashboard;

import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.util.List;

/**
 * 운영 중인 탐지·사건 설정. 화면이 "임계값을 넘었다"의 실제 기준을 보여줄 수 있도록 읽기 전용으로 내보낸다.
 *
 * 시간 값은 초 단위를 잃지 않도록 소수 분(예: 30초 = 0.5)으로 내보낸다.
 *
 * @param cooldownMinutes 같은 종목·규칙 신호를 다시 알리기 전 최소 간격. 기관 수급 규칙은 자체 간격을 쓴다
 *                        (규칙의 reAlertMinutes 설정값).
 */
public record DetectionSettingsView(
        BigDecimal cooldownMinutes,
        List<Rule> rules,
        Cases cases
) {
    /** 규칙 하나와 그 규칙이 쓰는 설정값들. */
    public record Rule(RuleType ruleType, List<Parameter> parameters) {
    }

    /** 화면에 그대로 보여줄 설정값 하나. */
    public record Parameter(String key, String label, BigDecimal value, String unit) {
    }

    /**
     * @param groupingWindowMinutes     같은 종목 신호를 한 사건으로 묶는 간격
     * @param inactivityTimeoutMinutes  새 신호가 없으면 자동 종결하는 시간
     * @param reactivationWindowMinutes 종결된 사건을 다시 여는 시간
     */
    public record Cases(
            BigDecimal groupingWindowMinutes,
            BigDecimal inactivityTimeoutMinutes,
            BigDecimal reactivationWindowMinutes,
            int simultaneousSignalBonus,
            int maxScore,
            int mediumThreshold,
            int highThreshold,
            List<Weight> ruleWeights
    ) {
    }

    public record Weight(RuleType ruleType, int points) {
    }
}
