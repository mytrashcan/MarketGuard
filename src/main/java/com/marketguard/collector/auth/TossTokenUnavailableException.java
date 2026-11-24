package com.marketguard.collector.auth;

import java.io.Serial;
import java.time.Instant;

/** 직전 토큰 발급이 실패해 재시도 대기 중일 때, 토스를 다시 호출하지 않고 바로 실패시킨다. */
public final class TossTokenUnavailableException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Instant nextAttemptAt;

    public TossTokenUnavailableException(Instant nextAttemptAt) {
        super("토스 토큰 발급이 직전에 실패해 " + nextAttemptAt + "까지 다시 시도하지 않습니다.");
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }
}
