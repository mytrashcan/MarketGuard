package com.marketguard.collector.client;

import java.time.Duration;
import java.io.Serial;
import org.springframework.http.HttpStatusCode;

/** Sanitized upstream failure that never retains or exposes a response body. */
public final class TossApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatusCode status;
    private final Duration retryAfter;
    private final String requestId;

    public TossApiException(HttpStatusCode status, Duration retryAfter, String requestId) {
        super("Toss API request failed with HTTP " + status.value());
        this.status = status;
        this.retryAfter = retryAfter;
        this.requestId = requestId;
    }

    public HttpStatusCode status() {
        return status;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public String requestId() {
        return requestId;
    }

    public boolean isRetryable() {
        return status.value() == 429 || status.is5xxServerError();
    }
}
