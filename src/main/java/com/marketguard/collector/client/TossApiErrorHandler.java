package com.marketguard.collector.client;

import com.marketguard.config.TossHttpProperties;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

public final class TossApiErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

    private final Duration maxRetryAfter;

    public TossApiErrorHandler(TossHttpProperties properties) {
        this.maxRetryAfter = properties.maxRetryAfter();
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        Duration retryAfter = parseRetryAfter(response.getHeaders().getFirst("Retry-After"));
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        throw new TossApiException(response.getStatusCode(), retryAfter, requestId);
    }

    private Duration parseRetryAfter(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            Duration parsed = Duration.ofSeconds(Long.parseLong(rawValue.trim()));
            if (parsed.isNegative()) {
                return null;
            }
            return parsed.compareTo(maxRetryAfter) > 0 ? maxRetryAfter : parsed;
        } catch (NumberFormatException ignored) {
            // Toss documents delta-seconds. Unsupported HTTP-date values fall back to exponential backoff.
            return null;
        }
    }
}
