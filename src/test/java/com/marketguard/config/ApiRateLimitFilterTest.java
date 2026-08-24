package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {

    @Test
    void limitsApiRequestsPerRemoteAddress() throws Exception {
        MarketGuardSecurityProperties properties =
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", "", List.of(), 2, 0);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties,
                Clock.fixed(Instant.parse("2026-07-15T00:00:30Z"), ZoneOffset.UTC));

        assertThat(invoke(filter, "192.0.2.1").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "192.0.2.1").getStatus()).isEqualTo(200);

        MockHttpServletResponse rejected = invoke(filter, "192.0.2.1");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("30");
        assertThat(rejected.getContentAsString()).doesNotContain("192.0.2.1");
        assertThat(invoke(filter, "192.0.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void resolvesClientFromForwardedForWhenProxiesAreTrusted() throws Exception {
        MarketGuardSecurityProperties properties =
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", "", List.of(), 2, 1);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties,
                Clock.fixed(Instant.parse("2026-07-15T00:00:30Z"), ZoneOffset.UTC));

        // One trusted proxy: client is the last X-Forwarded-For entry, so 203.0.113.7 hits the limit.
        assertThat(invoke(filter, "10.0.0.1",
                "203.0.113.7, 10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "10.0.0.1",
                "203.0.113.7, 10.0.0.1").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "10.0.0.1",
                "203.0.113.7, 10.0.0.1").getStatus()).isEqualTo(429);

        // A different forwarded client behind the same proxy is tracked separately.
        assertThat(invoke(filter, "10.0.0.1",
                "203.0.113.9, 10.0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void ignoresForwardedForWhenNoProxyIsTrusted() throws Exception {
        MarketGuardSecurityProperties properties =
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", "", List.of(), 1, 0);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties,
                Clock.fixed(Instant.parse("2026-07-15T00:00:30Z"), ZoneOffset.UTC));

        assertThat(invoke(filter, "192.0.2.1",
                "203.0.113.7").getStatus()).isEqualTo(200);
        // Spoofed header must not create a fresh bucket for the socket address.
        assertThat(invoke(filter, "192.0.2.1",
                "203.0.113.8").getStatus()).isEqualTo(429);
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedChainIsShorterThanTrustedHops() throws Exception {
        MarketGuardSecurityProperties properties =
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", "", List.of(), 1, 3);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(properties,
                Clock.fixed(Instant.parse("2026-07-15T00:00:30Z"), ZoneOffset.UTC));

        assertThat(invoke(filter, "192.0.2.1", "203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(invoke(filter, "192.0.2.1", "203.0.113.8").getStatus()).isEqualTo(429);
    }

    private static MockHttpServletResponse invoke(ApiRateLimitFilter filter, String remoteAddress) throws Exception {
        return invoke(filter, remoteAddress, null);
    }

    private static MockHttpServletResponse invoke(ApiRateLimitFilter filter, String remoteAddress,
                                                  String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anomalies");
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
