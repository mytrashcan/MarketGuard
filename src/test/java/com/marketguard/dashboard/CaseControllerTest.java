package com.marketguard.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.marketguard.application.CaseReviewService;
import com.marketguard.config.MarketGuardSecurityProperties;
import com.marketguard.detection.casework.AttentionLevel;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.domain.casework.SurveillanceCase;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CaseController.class)
@AutoConfigureMockMvc(addFilters = false)
class CaseControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    CaseQueryService queryService;
    @MockitoBean
    CaseReviewService reviewService;
    @MockitoBean
    MarketGuardSecurityProperties securityProperties;
    @MockitoBean
    Clock clock;

    @Test
    void returnsBoundedPageContractAndFilters() throws Exception {
        CaseSummaryView summary = new CaseSummaryView(1L, "005930", "삼성전자", "거래량 급증",
                "평균의 4.9배", 25, AttentionLevel.LOW, 1,
                Instant.parse("2026-07-15T01:00:00Z"), Instant.parse("2026-07-15T01:01:00Z"),
                CaseStatus.NEW, List.of("장중"), 0L);
        when(queryService.find(eq(CaseStatus.NEW), any(), any(), eq("005930"), any(), any(),
                any(), any(), any())).thenReturn(new PageImpl<>(List.of(summary)));

        mockMvc.perform(get("/api/cases").queryParam("status", "NEW")
                        .queryParam("stockCode", "005930").queryParam("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].stockName").value("삼성전자"))
                .andExpect(jsonPath("$.content[0].score").value(25))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void rejectsUnboundedOrInvertedQueriesBeforeRepositoryAccess() throws Exception {
        mockMvc.perform(get("/api/cases").queryParam("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/cases").queryParam("minimumScore", "80")
                        .queryParam("maximumScore", "20"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(queryService);
    }

    @Test
    void acceptsSupportedMarketSymbolsAndRejectsArbitraryInstrumentNames() throws Exception {
        when(queryService.find(any(), any(), any(), eq("KOSPI"), any(), any(),
                any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/cases").queryParam("stockCode", "KOSPI"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cases").queryParam("stockCode", "SYNTHETIC"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatesStatusUsingTheAuthenticatedPrincipalAndVersion() throws Exception {
        SurveillanceCase value = org.mockito.Mockito.mock(SurveillanceCase.class);
        when(value.getId()).thenReturn(1L);
        when(value.getStockCode()).thenReturn("005930");
        when(value.getStockName()).thenReturn("삼성전자");
        when(value.getTitle()).thenReturn("가격 급변동");
        when(value.getSummary()).thenReturn("요약");
        when(value.getAttentionLevel()).thenReturn(AttentionLevel.LOW);
        when(value.getStatus()).thenReturn(CaseStatus.REVIEWING);
        when(value.getContextTags()).thenReturn(List.of());
        when(reviewService.updateStatus(eq(1L), eq(2L), eq(CaseStatus.REVIEWING),
                any(), any(), eq("operator"))).thenReturn(value);

        mockMvc.perform(patch("/api/cases/1/status").principal(() -> "operator")
                        .contentType("application/json")
                        .content("{\"status\":\"REVIEWING\",\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWING"));
    }
}
