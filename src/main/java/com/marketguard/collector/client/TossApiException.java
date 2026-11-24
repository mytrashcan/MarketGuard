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
    private final Duration requestedRetryAfter;
    private final String requestId;

    public TossApiException(HttpStatusCode status, Duration retryAfter, String requestId) {
        this(status, retryAfter, retryAfter, requestId);
    }

    /**
     * @param retryAfter          Retry-After capped for retries inside one call (toss.http.max-retry-after)
     * @param requestedRetryAfter Retry-After exactly as Toss sent it, for callers that wait across calls
     */
    public TossApiException(HttpStatusCode status, Duration retryAfter, Duration requestedRetryAfter,
                            String requestId) {
        super("Toss API request failed with HTTP " + status.value());
        this.status = status;
        this.retryAfter = retryAfter;
        this.requestedRetryAfter = requestedRetryAfter;
        this.requestId = requestId;
    }

    public HttpStatusCode status() {
        return status;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    /** Toss가 보낸 Retry-After 그대로(상한 없음). 없으면 null. */
    public Duration requestedRetryAfter() {
        return requestedRetryAfter;
    }

    public String requestId() {
        return requestId;
    }

    public boolean isRetryable() {
        return status.value() == 429 || status.is5xxServerError();
    }
}
