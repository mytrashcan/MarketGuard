package com.marketguard.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Records bounded endpoint/status tags without retaining request or response bodies. */
final class TossHttpMetricsInterceptor implements ClientHttpRequestInterceptor {

    private final MeterRegistry registry;
    private final String client;

    TossHttpMetricsInterceptor(MeterRegistry registry, String client) {
        this.registry = registry;
        this.client = client;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Timer.Sample sample = Timer.start(registry);
        try {
            ClientHttpResponse response = execution.execute(request, body);
            stop(sample, endpoint(request.getURI()), Integer.toString(response.getStatusCode().value()));
            return response;
        } catch (IOException exception) {
            stop(sample, endpoint(request.getURI()), "io_error");
            throw exception;
        }
    }

    private void stop(Timer.Sample sample, String endpoint, String outcome) {
        sample.stop(Timer.builder("marketguard.toss.http.duration")
                .description("Toss HTTP request duration per attempt")
                .tag("client", client)
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(registry));
    }

    private static String endpoint(URI uri) {
        String path = uri.getPath();
        if (path.endsWith("/oauth2/token")) {
            return "token";
        }
        if (path.endsWith("/prices")) {
            return "prices";
        }
        if (path.endsWith("/price-limits")) {
            return "price_limits";
        }
        if (path.endsWith("/orderbook")) {
            return "orderbook";
        }
        if (path.endsWith("/candles")) {
            return "candles";
        }
        if (path.endsWith("/warnings")) {
            return "warnings";
        }
        if (path.endsWith("/market-calendar/KR")) {
            return "calendar";
        }
        return "other";
    }
}
