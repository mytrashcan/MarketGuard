package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketguard.application.port.StockNameResolver;
import com.marketguard.config.SurveillanceProperties;
import com.marketguard.detection.casework.AttentionLevel;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.casework.CaseNoteRepository;
import com.marketguard.domain.casework.CaseStatusHistoryRepository;
import com.marketguard.domain.casework.SurveillanceCase;
import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class CaseQueryServiceTest {

    @Test
    void enrichesMigratedCodeOnlyNamesFromTheCurrentReferenceCache() {
        SurveillanceCaseRepository caseRepository = mock(SurveillanceCaseRepository.class);
        AnomalyRepository anomalyRepository = mock(AnomalyRepository.class);
        CaseNoteRepository noteRepository = mock(CaseNoteRepository.class);
        CaseStatusHistoryRepository historyRepository = mock(CaseStatusHistoryRepository.class);
        StockNameResolver nameResolver = code -> "삼성전자";
        Pageable pageable = Pageable.ofSize(25);
        SurveillanceCase value = mock(SurveillanceCase.class);
        Instant detectedAt = Instant.parse("2026-07-15T01:00:00Z");
        when(value.getId()).thenReturn(1L);
        when(value.getStockCode()).thenReturn("005930");
        when(value.getStockName()).thenReturn("005930");
        when(value.getTitle()).thenReturn("거래량 급증");
        when(value.getSummary()).thenReturn("직전 평균보다 거래량이 증가했습니다.");
        when(value.getAttentionLevel()).thenReturn(AttentionLevel.LOW);
        when(value.getStatus()).thenReturn(CaseStatus.NEW);
        when(value.getFirstDetectedAt()).thenReturn(detectedAt);
        when(value.getLastDetectedAt()).thenReturn(detectedAt);
        when(value.getContextTags()).thenReturn(List.of("장중"));
        when(caseRepository.findAll(org.mockito.ArgumentMatchers
                .<Specification<SurveillanceCase>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(value)));

        CaseQueryService service = new CaseQueryService(caseRepository, anomalyRepository,
                noteRepository, historyRepository, nameResolver, properties());

        CaseSummaryView result = service.find(null, null, null, null,
                null, null, null, null, pageable).getContent().get(0);

        assertThat(result.stockName()).isEqualTo("삼성전자");
        assertThat(result.stockCode()).isEqualTo("005930");
    }

    private static SurveillanceProperties properties() {
        return new SurveillanceProperties(Duration.ofMinutes(10), Duration.ofMinutes(20),
                Duration.ofMinutes(30), Map.of(), 10, 100, 30, 60);
    }
}
