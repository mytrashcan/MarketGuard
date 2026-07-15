package com.marketguard.dashboard;

import com.marketguard.detection.casework.CaseStatus;
import com.marketguard.detection.casework.ClosureReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull CaseStatus status,
        @Min(0) long version,
        ClosureReason reason,
        @Size(max = 500) String detail
) {
}
