package com.marketguard.dashboard;

import com.marketguard.detection.casework.AttentionLevel;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.domain.casework.SurveillanceCase;
import java.time.Instant;
import java.util.List;

public record CaseSummaryView(
        Long id,
        String stockCode,
        String stockName,
        String title,
        String summary,
        int score,
        AttentionLevel attentionLevel,
        int signalCount,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        CaseStatus status,
        List<String> contextTags,
        long version
) {
    static CaseSummaryView from(SurveillanceCase value) {
        return from(value, value.getStockName());
    }

    static CaseSummaryView from(SurveillanceCase value, String stockName) {
        return new CaseSummaryView(value.getId(), value.getStockCode(), stockName,
                value.getTitle(), value.getSummary(), value.getScore(), value.getAttentionLevel(),
                value.getSignalCount(), value.getFirstDetectedAt(), value.getLastDetectedAt(),
                value.getStatus(), value.getContextTags(), value.getVersion());
    }
}
