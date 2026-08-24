package com.marketguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Small bounded per-client fixed-window limiter for the operator read API.
 *
 * <p>Client identity comes from {@code getRemoteAddr()} unless trusted proxies are configured
 * (marketguard.security.trusted-proxies), in which case the client address is taken from the
 * X-Forwarded-For chain, counting the configured number of trusted proxy hops from the right.
 *
 * <p>State is kept in a per-key locked ConcurrentHashMap (no global lock on every /api/ request).
 * When the map exceeds its cap, entries from previous windows are purged first so that
 * active clients are not evicted mid-window by floods of one-shot clients.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final MarketGuardSecurityProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>(128);

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
        String client = clientAddress(request);
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

    /**
     * Resolves the originating client address. Only when at least one trusted reverse proxy is
     * configured do we look at X-Forwarded-For; we then walk the chain from right to left past
     * the trusted hops. A short or missing header falls back to the socket address, which cannot
     * be spoofed into bypassing the limit.
     */
    private String clientAddress(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
        int trustedHops = Math.max(properties.trustedProxies(), 0);
        if (trustedHops == 0) {
            return remoteAddr;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] chain = forwardedFor.split(",");
        int index = chain.length - trustedHops - 1;
        if (index < 0) {
            return remoteAddr;
        }
        String candidate = chain[index].trim();
        return candidate.isEmpty() ? remoteAddr : candidate;
    }

    /** Per-key atomic window update; never blocks requests for unrelated clients. */
    private boolean acquire(String client, long minute) {
        int limit = properties.requestsPerMinute();
        boolean[] allowed = {false};
        windows.compute(client, (key, window) -> {
            if (window == null || window.minute != minute) {
                allowed[0] = true;
                return new Window(minute, 1);
            }
            if (window.count >= limit) {
                allowed[0] = false;
                return window;
            }
            window.count++;
            allowed[0] = true;
            return window;
        });
        evictIfOverflowing(minute);
        return allowed[0];
    }

    /**
     * Bounded memory: first drop entries whose fixed window has already rolled over (stale),
     * then fall back to arbitrary eviction only if a single window genuinely exceeds the cap.
     */
    private void evictIfOverflowing(long currentMinute) {
        if (windows.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        windows.values().removeIf(window -> window.minute < currentMinute);
        while (windows.size() > MAX_TRACKED_CLIENTS) {
            Iterator<String> iterator = windows.keySet().iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }
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
