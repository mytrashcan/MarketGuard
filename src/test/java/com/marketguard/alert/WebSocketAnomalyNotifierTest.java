package com.marketguard.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.StockReferenceService;
import com.marketguard.detection.model.Anomaly;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class WebSocketAnomalyNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StockReferenceService stockReferenceService;

    @Test
    void publishesTheStockNameWithRealtimeAnomalies() {
        Anomaly anomaly = Anomaly.of(
                "005930", RuleType.PRICE_SPIKE, Severity.WARNING,
                "가격 급변동", Instant.parse("2026-07-15T01:00:00Z"));
        when(stockReferenceService.nameOf("005930")).thenReturn("삼성전자");
        WebSocketAnomalyNotifier notifier = new WebSocketAnomalyNotifier(
                messagingTemplate, stockReferenceService);
        ArgumentCaptor<AnomalyNotification> payload = ArgumentCaptor.forClass(AnomalyNotification.class);

        notifier.publish(anomaly);

        verify(messagingTemplate).convertAndSend(eq(WebSocketAnomalyNotifier.TOPIC), payload.capture());
        assertThat(payload.getValue().stockCode()).isEqualTo("005930");
        assertThat(payload.getValue().stockName()).isEqualTo("삼성전자");
    }
}
