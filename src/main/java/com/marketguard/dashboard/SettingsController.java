package com.marketguard.dashboard;

import com.marketguard.config.DetectionRuntimeProperties;
import com.marketguard.config.InstitutionalFlowProperties;
import com.marketguard.config.OrderbookImbalanceProperties;
import com.marketguard.config.PriceLimitProperties;
import com.marketguard.config.PriceSpikeProperties;
import com.marketguard.config.SurveillanceProperties;
import com.marketguard.config.VolumeSurgeProperties;
import com.marketguard.dashboard.DetectionSettingsView.Parameter;
import com.marketguard.dashboard.DetectionSettingsView.Rule;
import com.marketguard.detection.model.RuleType;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 운영 중인 탐지 임계값과 사건 처리 설정(읽기 전용). 비밀 값은 포함하지 않는다. */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final DetectionSettingsView settings;

    public SettingsController(DetectionRuntimeProperties runtime,
                              PriceSpikeProperties priceSpike,
                              PriceLimitProperties priceLimit,
                              OrderbookImbalanceProperties orderbook,
                              VolumeSurgeProperties volume,
                              InstitutionalFlowProperties institutional,
                              SurveillanceProperties surveillance) {
        // 설정은 기동 후 바뀌지 않으므로 한 번만 만든다.
        this.settings = new DetectionSettingsView(
                minutes(Duration.ofMillis(runtime.cooldownMs())),
                rules(priceSpike, priceLimit, orderbook, volume, institutional),
                cases(surveillance));
    }

    @GetMapping("/detection")
    public DetectionSettingsView detection() {
        return settings;
    }

    private static List<Rule> rules(PriceSpikeProperties price, PriceLimitProperties limit,
                                    OrderbookImbalanceProperties orderbook, VolumeSurgeProperties volume,
                                    InstitutionalFlowProperties institutional) {
        Parameter priceThreshold = new Parameter("thresholdPercent", "평균 대비 변동", price.thresholdPercent(), "%");
        Parameter priceLookback = new Parameter("priceLookback", "비교할 최근 스냅샷", BigDecimal.valueOf(price.lookback()), "개");
        Parameter priceMaxAge = new Parameter("maxAgeMinutes", "이보다 오래된 스냅샷 제외",
                minutes(price.maxAge()), "분");
        Parameter volumeMultiplier = new Parameter("multiplier", "직전 평균 대비 거래량", volume.multiplier(), "배");
        Parameter volumeLookback = new Parameter("volumeLookback", "비교할 직전 1분봉", BigDecimal.valueOf(volume.lookback()), "개");
        return List.of(
                new Rule(RuleType.PRICE_SPIKE, List.of(priceThreshold, priceLookback, priceMaxAge)),
                new Rule(RuleType.PRICE_LIMIT, List.of(
                        new Parameter("proximityPercent", "상·하한가까지 남은 거리", limit.proximityPercent(), "%"))),
                new Rule(RuleType.ORDERBOOK_IMBALANCE, List.of(
                        new Parameter("ratioThreshold", "매수·매도 잔량 비율", orderbook.ratioThreshold(), "배"))),
                new Rule(RuleType.VOLUME_SURGE, List.of(volumeMultiplier, volumeLookback)),
                new Rule(RuleType.PRICE_VOLUME_SURGE, List.of(priceThreshold, volumeMultiplier)),
                new Rule(RuleType.INVESTMENT_WARNING, List.of()),
                new Rule(RuleType.INSTITUTIONAL_NET_BUY_SURGE, institutionalParameters(institutional)),
                new Rule(RuleType.INSTITUTIONAL_NET_SELL_SURGE, institutionalParameters(institutional)));
    }

    private static List<Parameter> institutionalParameters(InstitutionalFlowProperties properties) {
        return List.of(
                new Parameter("multiplier", "최근 중앙값 대비", properties.multiplier(), "배"),
                new Parameter("minimumNetAmountEok", "최소 금액",
                        properties.minimumNetAmount().movePointLeft(8).stripTrailingZeros(), "억원"),
                new Parameter("lookback", "비교할 직전 거래일", BigDecimal.valueOf(properties.lookback()), "일"),
                // 기관 수급은 종목 공통 알림 간격이 아니라 이 간격으로 같은 시장·방향 재알림을 막는다.
                new Parameter("reAlertMinutes", "같은 시장·방향 재알림 간격", minutes(properties.cooldown()), "분"));
    }

    private static DetectionSettingsView.Cases cases(SurveillanceProperties properties) {
        return new DetectionSettingsView.Cases(
                minutes(properties.groupingWindow()),
                minutes(properties.inactivityTimeout()),
                minutes(properties.reactivationWindow()),
                properties.simultaneousSignalBonus(),
                properties.maxScore(),
                properties.mediumThreshold(),
                properties.highThreshold(),
                Arrays.stream(RuleType.values())
                        .map(rule -> new DetectionSettingsView.Weight(rule,
                                properties.ruleWeights().getOrDefault(rule, 0)))
                        .toList());
    }

    /** 초 단위를 잃지 않는 분 값(30초 → 0.5, 90초 → 1.5). 밀리초까지 보존한다. */
    static BigDecimal minutes(Duration duration) {
        return BigDecimal.valueOf(duration.toMillis())
                .divide(BigDecimal.valueOf(60_000), 6, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
    }
}
