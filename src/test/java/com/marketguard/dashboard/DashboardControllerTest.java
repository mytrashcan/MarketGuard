package com.marketguard.dashboard;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.MarketRankingQuote;
import com.marketguard.collector.MarketRankingService;
import com.marketguard.collector.MarketRankingType;
import com.marketguard.collector.StockReferenceService;
import com.marketguard.collector.client.TossApiException;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.MarketGuardSecurityProperties;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.audit.AuditLogRepository;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnomalyRepository anomalyRepository;

    @MockitoBean
    private PriceSnapshotRepository snapshotRepository;

    @MockitoBean
    private TossMarketDataClient marketDataClient;

    @MockitoBean
    private BoardDataService boardDataService;

    @MockitoBean
    private MarketRankingService marketRankingService;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private StockReferenceService stockReferenceService;

    @MockitoBean
    private MarketGuardSecurityProperties securityProperties;

    @MockitoBean
    private Clock clock;

    @Test
    void rejectsMalformedCandleParametersBeforeCallingUpstream() throws Exception {
        mockMvc.perform(get("/api/stocks/not-a-symbol/candles")
                        .queryParam("interval", "unsupported")
                        .queryParam("count", "10000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(marketDataClient);
    }

    @Test
    void rejectsOutOfRangeListLimit() throws Exception {
        mockMvc.perform(get("/api/anomalies").queryParam("limit", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(anomalyRepository);
    }

    @Test
    void returnsTheCachedTossRankingWithOfficialRankAndStockName() throws Exception {
        when(marketRankingService.current(MarketRankingType.MARKET_TRADING_AMOUNT))
                .thenReturn(List.of(new MarketRankingQuote(
                        2, "005930", new BigDecimal("72000"), new BigDecimal("70000"),
                        new BigDecimal("0.0286"), 123456L, 8_888_888L,
                        Instant.parse("2026-07-15T01:00:00Z"))));
        when(stockReferenceService.nameOf("005930")).thenReturn("삼성전자");

        mockMvc.perform(get("/api/rankings")
                        .queryParam("type", "MARKET_TRADING_AMOUNT")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(2))
                .andExpect(jsonPath("$[0].code").value("005930"))
                .andExpect(jsonPath("$[0].name").value("삼성전자"))
                .andExpect(jsonPath("$[0].changePercent").value(2.8600))
                .andExpect(jsonPath("$[0].tradingAmount").value(8_888_888L));
    }

    @Test
    void rejectsUnsupportedRankingTypeAndLimit() throws Exception {
        mockMvc.perform(get("/api/rankings")
                        .queryParam("type", "UNSUPPORTED")
                        .queryParam("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(marketRankingService);
    }

    @Test
    void mapsRetryableUpstreamFailureToSafeServiceUnavailableResponse() throws Exception {
        when(marketDataClient.fetchCandles("005930", "1m", 60))
                .thenThrow(new TossApiException(HttpStatus.TOO_MANY_REQUESTS, Duration.ofSeconds(1), "request-1"));

        mockMvc.perform(get("/api/stocks/005930/candles"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MARKET_DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Market data is temporarily unavailable"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("request-1"))));
    }

    @Test
    void includesTheOfficialStockNameInAnomalyResponses() throws Exception {
        AnomalyRecord anomaly = new AnomalyRecord(
                "005930", RuleType.PRICE_SPIKE, Severity.WARNING,
                "가격 급변동", Instant.parse("2026-07-15T01:00:00Z"));
        when(anomalyRepository.findByOrderByDetectedAtDesc(any(Limit.class)))
                .thenReturn(List.of(anomaly));
        when(stockReferenceService.nameOf("005930")).thenReturn("삼성전자");

        mockMvc.perform(get("/api/anomalies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stockCode").value("005930"))
                .andExpect(jsonPath("$[0].stockName").value("삼성전자"));
    }
}
