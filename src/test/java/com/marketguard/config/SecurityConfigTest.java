package com.marketguard.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class SecurityConfigTest {

    @Test
    void configuresLocalCsrfCookieAsSameSiteLaxWithoutSecure() {
        assertCsrfCookie(false, false);
    }

    @Test
    void configuresProductionCsrfCookieAsSameSiteLaxAndSecure() {
        assertCsrfCookie(true, true);
    }

    private static void assertCsrfCookie(boolean securityEnabled, boolean expectedSecure) {
        MarketGuardSecurityProperties properties =
                new MarketGuardSecurityProperties(securityEnabled, "", "", "", List.of(), 120, 0);
        CookieCsrfTokenRepository repository = SecurityConfig.csrfTokenRepository(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/csrf");
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token"), request, response);

        assertThat(response.getCookie("XSRF-TOKEN")).isNotNull().satisfies(cookie -> {
            assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
            assertThat(cookie.getSecure()).isEqualTo(expectedSecure);
            assertThat(cookie.isHttpOnly()).isFalse();
        });
    }
}
