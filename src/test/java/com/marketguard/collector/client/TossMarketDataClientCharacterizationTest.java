package com.marketguard.collector.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.marketguard.config.ResilienceConfig;
import com.marketguard.config.RestClientConfig;
import com.marketguard.config.TossHttpProperties;
import com.marketguard.collector.MarketRankingQuote;
import com.marketguard.detection.model.Candle;
import com.marketguard.detection.model.MarketPrice;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossMarketDataClientCharacterizationTest {

    private static final String BASE_URL = "https://example.test";
    private static final TossHttpProperties HTTP_PROPERTIES = new TossHttpProperties(
            Duration.ofSeconds(1), Duration.ofSeconds(1), 3,
            Duration.ofMillis(1), Duration.ofMillis(5), 0.0, Duration.ofSeconds(2));

    @Test
    void preservesTheOfficialMarketTimestamp() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
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
        List<MarketPrice> result = client.fetchPrices(List.of("005930"));

        assertThat(result).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.capturedAt()).isEqualTo(Instant.parse("2026-03-25T00:30:00.123Z"));
            assertThat(snapshot.price()).isEqualByComparingTo("72000");
        });
        server.verify();
    }

    @Test
    void returnsAnEmptyListForAnEmptySuccessfulEnvelope() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL + "/api/v1/prices?symbols=005930"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPrices(List.of("005930"))).isEmpty();
        server.verify();
    }

    @Test
    void requestsUnadjustedDailyCandlesWhenExplicitlyRequired() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL
                        + "/api/v1/candles?symbol=005930&interval=1d&count=2&adjusted=false"))
                .andRespond(withSuccess("""
                        {"result":{"candles":[{
                          "timestamp":"2026-07-15T09:00:00+09:00",
                          "openPrice":"70000","highPrice":"73000","lowPrice":"69000",
                          "closePrice":"72000","volume":"123456"
                        }]}}
                        """, MediaType.APPLICATION_JSON));

        List<Candle> result = client.fetchCandles("005930", "1d", 2, false);

        assertThat(result).singleElement().satisfies(candle -> {
            assertThat(candle.close()).isEqualByComparingTo("72000");
            assertThat(candle.volume()).isEqualTo(123456L);
        });
        server.verify();
    }

    @Test
    void requestsCandlesAtOrBeforeTheCaseChartBoundary() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL
                        + "/api/v1/candles?symbol=005930&interval=1m&count=120"
                        + "&before=2026-07-15T02:00:00Z"))
                .andRespond(withSuccess("{\"result\":{\"candles\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCandlesBefore(
                "005930", "1m", 120, Instant.parse("2026-07-15T02:00:00Z"))).isEmpty();
        server.verify();
    }

    @Test
    void mapsOfficialRankingBasePriceAndTradingVolume() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL + "/api/v1/rankings?type=MARKET_TRADING_VOLUME"
                        + "&marketCountry=KR&duration=realtime&count=100"))
                .andRespond(withSuccess("""
                        {"result":{"rankedAt":"2026-07-15T10:00:00+09:00","rankings":[{
                          "rank":1,"symbol":"005930","currency":"KRW",
                          "price":{"lastPrice":"72000","basePrice":"70000","changeRate":"0.0286"},
                          "tradingVolume":"123456","tradingAmount":"8888888"
                        }]}}
                        """, MediaType.APPLICATION_JSON));

        List<MarketRankingQuote> result = client.fetchKrRealtimeVolumeRanking();

        assertThat(result).singleElement().satisfies(quote -> {
            assertThat(quote.stockCode()).isEqualTo("005930");
            assertThat(quote.lastPrice()).isEqualByComparingTo("72000");
            assertThat(quote.basePrice()).isEqualByComparingTo("70000");
            assertThat(quote.changeRate()).isEqualByComparingTo("0.0286");
            assertThat(quote.tradingVolume()).isEqualTo(123456L);
            assertThat(quote.rankedAt()).isEqualTo(Instant.parse("2026-07-15T01:00:00Z"));
        });
        server.verify();
    }

    @Test
    void mapsOfficialKoreanStockNames() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL + "/api/v1/stocks?symbols=005930,000660"))
                .andRespond(withSuccess("""
                        {"result":[
                          {"symbol":"005930","name":"삼성전자"},
                          {"symbol":"000660","name":"SK하이닉스"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Map<String, String> result = client.fetchStockNames(List.of("005930", "000660"));

        assertThat(result).containsEntry("005930", "삼성전자")
                .containsEntry("000660", "SK하이닉스");
        server.verify();
    }

    @Test
    void doesNotRetryPermanentClientErrorsOrRetainTheirBody() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(requestTo(BASE_URL + "/api/v1/prices?symbols=005930"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":{\"message\":\"sensitive upstream detail\"}}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPrices(List.of("005930")))
                .isInstanceOf(TossApiException.class)
                .hasMessage("Toss API request failed with HTTP 400")
                .hasMessageNotContaining("sensitive");
        server.verify();
    }

    @Test
    void retriesRateLimitsAndServerErrors() {
        assertRetried(HttpStatus.TOO_MANY_REQUESTS);
        assertRetried(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void rejectsInvalidInputWithoutAnHttpCall() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);

        assertThatThrownBy(() -> client.fetchCandles("../secret", "1h", 1_000))
                .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    private void assertRetried(HttpStatus status) {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TossMarketDataClient client = client(builder);
        server.expect(ExpectedCount.times(3), requestTo(BASE_URL + "/api/v1/prices?symbols=005930"))
                .andRespond(withStatus(status).header("Retry-After", "0"));

        assertThatThrownBy(() -> client.fetchPrices(List.of("005930")))
                .isInstanceOf(TossApiException.class);
        server.verify();
    }

    private static RestClient.Builder configuredBuilder() {
        return new RestClientConfig().baseBuilder(HTTP_PROPERTIES).baseUrl(BASE_URL);
    }

    private static TossMarketDataClient client(RestClient.Builder builder) {
        return new TossMarketDataClient(
                builder.build(), CircuitBreaker.ofDefaults("test"),
                new ResilienceConfig().tossMarketRetry(HTTP_PROPERTIES),
                RateLimiter.ofDefaults("market"), RateLimiter.ofDefaults("chart"),
                RateLimiter.ofDefaults("stock"), RateLimiter.ofDefaults("calendar"),
                RateLimiter.ofDefaults("ranking"));
    }
}
