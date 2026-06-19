package com.marketguard.alert;

import com.marketguard.detection.model.Anomaly;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 이상 신호를 STOMP 토픽 /topic/anomalies 로 실시간 푸시한다.
 * 대시보드(static/index.html)가 이 토픽을 구독해 즉시 표시한다.
 */
@Component
public class WebSocketAnomalyNotifier implements AnomalyNotifier {

    public static final String TOPIC = "/topic/anomalies";

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketAnomalyNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publish(Anomaly anomaly) {
        messagingTemplate.convertAndSend(TOPIC, anomaly);
    }
}
