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
        Duration requested = parseRetryAfter(response.getHeaders().getFirst("Retry-After"));
        // 한 호출 안의 재시도는 상한까지만 기다리고, 호출 사이의 대기(토큰 발급 등)는 Toss가 요청한 만큼 기다린다.
        Duration capped = requested == null || requested.compareTo(maxRetryAfter) <= 0 ? requested : maxRetryAfter;
        String requestId = response.getHeaders().getFirst("X-Request-Id");
        throw new TossApiException(response.getStatusCode(), capped, requested, requestId);
    }

    private static Duration parseRetryAfter(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        try {
            Duration parsed = Duration.ofSeconds(Long.parseLong(rawValue.trim()));
            return parsed.isNegative() ? null : parsed;
        } catch (NumberFormatException ignored) {
            // Toss documents delta-seconds. Unsupported HTTP-date values fall back to exponential backoff.
            return null;
        }
    }
}
