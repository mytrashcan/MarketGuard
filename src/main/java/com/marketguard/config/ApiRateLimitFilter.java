package com.marketguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Small bounded per-client fixed-window limiter for the operator read API. */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final MarketGuardSecurityProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
            return size() > MAX_TRACKED_CLIENTS;
        }
    };

    public ApiRateLimitFilter(MarketGuardSecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long currentMinute = clock.instant().getEpochSecond() / 60;
        String client = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        if (!acquire(client, currentMinute)) {
            long retryAfter = 60 - (clock.instant().getEpochSecond() % 60);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private synchronized boolean acquire(String client, long minute) {
        Window window = windows.get(client);
        if (window == null || window.minute != minute) {
            windows.put(client, new Window(minute, 1));
            return true;
        }
        if (window.count >= properties.requestsPerMinute()) {
            return false;
        }
        window.count++;
        return true;
    }

    private static final class Window {
        private final long minute;
        private int count;

        private Window(long minute, int count) {
            this.minute = minute;
            this.count = count;
        }
    }
}
