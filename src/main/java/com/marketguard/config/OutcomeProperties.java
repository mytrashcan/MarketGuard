package com.marketguard.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 신호 사후 결과 측정 설정.
 *
 * @param horizon       신호 후 몇 분 뒤 가격을 볼지
 * @param matchWindow   신호·측정 시점에서 이 시간 안의 스냅샷만 가격으로 인정
 * @param measureInterval 측정 작업 주기
 */
@Validated
@ConfigurationProperties(prefix = "outcome")
public record OutcomeProperties(
        @NotNull Duration horizon,
        @NotNull Duration matchWindow,
        @NotNull Duration measureInterval
) {
    public OutcomeProperties {
        if (horizon != null && (horizon.compareTo(Duration.ofMinutes(1)) < 0 || horizon.compareTo(Duration.ofHours(6)) > 0)) {
            throw new IllegalArgumentException("outcome horizon must be between 1 minute and 6 hours");
        }
        if (matchWindow != null && (matchWindow.compareTo(Duration.ofSeconds(30)) < 0
                || matchWindow.compareTo(Duration.ofMinutes(10)) > 0)) {
            throw new IllegalArgumentException("outcome match window must be between 30 seconds and 10 minutes");
        }
        if (measureInterval != null && measureInterval.compareTo(Duration.ofSeconds(10)) < 0) {
            throw new IllegalArgumentException("outcome measure interval must be at least 10 seconds");
        }
    }

    /** 측정에 필요한 스냅샷이 남아 있어야 하는 가장 긴 시간. 스냅샷 보존 기간이 이보다 길어야 한다. */
    public Duration requiredSnapshotRetention() {
        return horizon.plus(matchWindow.multipliedBy(2)).plus(measureInterval);
    }
}
