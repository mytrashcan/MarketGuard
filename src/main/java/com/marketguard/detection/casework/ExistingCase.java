package com.marketguard.detection.casework;

import java.time.Instant;
import java.util.Objects;

public record ExistingCase(CaseStatus status, Instant lastDetectedAt, Instant closedAt) {
    public ExistingCase {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(lastDetectedAt, "lastDetectedAt must not be null");
    }
}
