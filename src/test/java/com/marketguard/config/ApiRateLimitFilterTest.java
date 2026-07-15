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
                new MarketGuardSecurityProperties(true, "operator", "a-secure-password", List.of(), 2);
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

    private static MockHttpServletResponse invoke(ApiRateLimitFilter filter, String remoteAddress) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/anomalies");
        request.setRemoteAddr(remoteAddress);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
