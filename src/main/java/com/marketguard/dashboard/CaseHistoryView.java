package com.marketguard.dashboard;

import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.ClosureReason;
import com.marketguard.domain.casework.CaseStatusHistory;
import java.time.Instant;

public record CaseHistoryView(
        Long id,
        CaseStatus fromStatus,
        CaseStatus toStatus,
        String actor,
        ClosureReason reason,
        String detail,
        Instant changedAt
) {
    static CaseHistoryView from(CaseStatusHistory value) {
        return new CaseHistoryView(value.getId(), value.getFromStatus(), value.getToStatus(),
                value.getActor(), value.getReason(), value.getDetail(), value.getChangedAt());
    }
}
