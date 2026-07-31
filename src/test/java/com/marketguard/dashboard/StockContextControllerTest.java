package com.marketguard.dashboard;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.StockReferenceService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.config.MarketGuardSecurityProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StockContextController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockContextControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BoardDataService boardDataService;

    @MockitoBean
    private StockReferenceService stockReferenceService;

    @MockitoBean
    private TossMarketDataClient marketDataClient;

    @MockitoBean
    private MarketGuardSecurityProperties securityProperties;

    @MockitoBean
    private Clock clock;

    @Test
    void forwardsTheRequestedCaseChartBoundaryToTheMarketDataClient() throws Exception {
        Instant before = Instant.parse("2026-07-15T02:00:00Z");
        when(boardDataService.currentBoard()).thenReturn(List.of());
        when(marketDataClient.fetchCandlesBefore("005930", "1m", 120, before))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/stocks/005930/context")
                        .queryParam("count", "120")
                        .queryParam("before", before.toString()))
                .andExpect(status().isOk());

        verify(marketDataClient).fetchCandlesBefore("005930", "1m", 120, before);
    }
}
