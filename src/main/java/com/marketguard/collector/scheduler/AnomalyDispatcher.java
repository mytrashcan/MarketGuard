package com.marketguard.collector.scheduler;

import com.marketguard.alert.AnomalyNotifier;
import com.marketguard.application.AnomalyRecordingService;
import com.marketguard.detection.model.Anomaly;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 탐지된 신호를 영속화(쿨다운·사건 그룹화 포함)한 뒤, 새로 기록된 경우에만 모든 notifier에 전파한다.
 * 종목 스캐너와 시장 수급 스캐너가 같은 기록·알림 정책을 공유하도록 한곳에 모은다.
 */
@Slf4j
@Component
public class AnomalyDispatcher {

    private final AnomalyRecordingService recordingService;
    private final List<AnomalyNotifier> notifiers;
    private final CollectorObservability observability;

    public AnomalyDispatcher(AnomalyRecordingService recordingService,
                             List<AnomalyNotifier> notifiers,
                             CollectorObservability observability) {
        this.recordingService = recordingService;
        this.notifiers = List.copyOf(notifiers);
        this.observability = observability;
    }

    /**
     * 신호를 기록하고, 쿨다운을 통과해 새로 기록된 경우에만 알림을 보낸다.
     *
     * @return 새로 기록되어 알림까지 시도했으면 {@code true}, 쿨다운으로 억제됐으면 {@code false}
     */
    public boolean dispatch(Anomaly anomaly, Duration cooldown) {
        if (recordingService.recordIfEligible(anomaly, cooldown).isEmpty()) {
            return false;
        }
        observability.recordAnomaly();
        notifiers.forEach(notifier -> publishSafely(notifier, anomaly));
        log.info("[이상징후 기록] {} {} - {}", anomaly.stockCode(), anomaly.ruleType(), anomaly.message());
        return true;
    }

    private static void publishSafely(AnomalyNotifier notifier, Anomaly anomaly) {
        try {
            notifier.publish(anomaly);
        } catch (RuntimeException exception) {
            // 알림은 커밋 이후 best-effort: 한 notifier 실패가 다른 notifier나 수집을 막지 않는다.
            log.warn("Notifier {} failed for {} {} ({})",
                    notifier.getClass().getSimpleName(), anomaly.stockCode(), anomaly.ruleType(),
                    exception.getClass().getSimpleName());
        }
    }
}
