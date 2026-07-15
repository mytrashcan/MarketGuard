package com.marketguard.collector.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marketguard.domain.marketdata.PriceSnapshot;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossMarketDataClientCharacterizationTest {

    @Test
    void documentsThatOfficialTimestampIsCurrentlyReplacedWithLocalTime() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://example.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = new TossMarketDataClient(
                builder.build(), CircuitBreaker.ofDefaults("test"), Retry.ofDefaults("test"));
        server.expect(requestTo("https://example.test/api/v1/prices?symbols=005930"))
                .andRespond(withSuccess("""
                        {
                          "result": [{
                            "symbol": "005930",
                            "timestamp": "2026-03-25T09:30:00.123+09:00",
                            "lastPrice": "72000",
                            "currency": "KRW"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));
        Instant before = Instant.now();

        List<PriceSnapshot> result = client.fetchPrices(List.of("005930"));

        Instant after = Instant.now();
        assertThat(result).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getCapturedAt()).isBetween(before, after);
            assertThat(snapshot.getCapturedAt()).isNotEqualTo(Instant.parse("2026-03-25T00:30:00.123Z"));
        });
        server.verify();
    }
}
