package com.marketguard.alert;

import com.marketguard.detection.model.Anomaly;

/**
 * 탐지된 이상 신호를 외부로 전파한다(옵저버). 구현체를 추가하면
 * MarketDataCollector가 모든 notifier에 자동 전파한다(메일·Slack 등 확장 지점).
 */
public interface AnomalyNotifier {

    void publish(Anomaly anomaly);
}
