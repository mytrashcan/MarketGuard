package com.marketguard.dashboard;

import com.marketguard.detection.casework.AttentionLevel;
import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.ClosureReason;
import com.marketguard.detection.casework.ScoreContribution;
import java.time.Instant;
import java.util.List;

public record CaseDetailView(
        Long id,
        String stockCode,
        String stockName,
        String title,
        String summary,
        int score,
        AttentionLevel attentionLevel,
        String scoreExplanation,
        List<ScoreContribution> scoreContributions,
        int simultaneousSignalBonus,
        CaseStatus status,
        int signalCount,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        Instant closedAt,
        String reviewer,
        ClosureReason closureReason,
        String closureDetail,
        List<String> contextTags,
        long version,
        List<AnomalyView> signals,
        List<CaseNoteView> notes,
        List<CaseHistoryView> history,
        String caution
) {
}
