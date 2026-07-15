package com.marketguard.collector.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marketguard.collector.client.TossApiException;
import com.marketguard.config.ResilienceConfig;
import com.marketguard.config.RestClientConfig;
import com.marketguard.config.TossApiProperties;
import com.marketguard.config.TossHttpProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

class TossTokenClientTest {

    private static final String AUTH_URL = "https://example.test/oauth2/token";
    private static final TossHttpProperties HTTP_PROPERTIES = new TossHttpProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(1), 3,
            Duration.ofMillis(1), Duration.ofMillis(5), 0.0, Duration.ofSeconds(2));
    private static final TossApiProperties API_PROPERTIES = new TossApiProperties(
            "https://example.test", AUTH_URL, "client-id", "client-secret", List.of("005930"));

    @Test
    void issuesAClientCredentialsToken() {
        RestClient.Builder builder = new RestClientConfig().baseBuilder(HTTP_PROPERTIES);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var expectedForm = new LinkedMultiValueMap<String, String>();
        expectedForm.add("grant_type", "client_credentials");
        expectedForm.add("client_id", "client-id");
        expectedForm.add("client_secret", "client-secret");
        server.expect(requestTo(AUTH_URL))
                .andExpect(content().formData(expectedForm))
                .andRespond(withSuccess("""
                        {"access_token":"token-value","token_type":"Bearer","expires_in":86400}
                        """, MediaType.APPLICATION_JSON));

        TokenResponse response = client(builder).issue();

        assertThat(response.accessToken()).isEqualTo("token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(86_400);
        server.verify();
    }

    @Test
    void sanitizesPermanentAuthenticationFailuresWithoutRetrying() {
        RestClient.Builder builder = new RestClientConfig().baseBuilder(HTTP_PROPERTIES);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(AUTH_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error_description\":\"client-secret is invalid\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client(builder).issue())
                .isInstanceOf(TossApiException.class)
                .hasMessage("Toss API request failed with HTTP 401")
                .hasMessageNotContaining("client-secret");
        server.verify();
    }

    private static TossTokenClient client(RestClient.Builder builder) {
        ResilienceConfig resilience = new ResilienceConfig();
        return new TossTokenClient(
                API_PROPERTIES,
                builder.build(),
                CircuitBreaker.ofDefaults("auth-test"),
                resilience.tossAuthRetry(HTTP_PROPERTIES),
                RateLimiter.ofDefaults("auth-test"));
    }
}
