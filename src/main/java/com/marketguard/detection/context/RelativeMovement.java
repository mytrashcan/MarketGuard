package com.marketguard.detection.context;

import java.math.BigDecimal;

public record RelativeMovement(
        BigDecimal stockReturnPercent,
        BigDecimal benchmarkReturnPercent,
        BigDecimal excessReturnPercentagePoints,
        String benchmarkDescription,
        boolean available,
        String unavailableReason
) {
    public static RelativeMovement unavailable(String reason) {
        return new RelativeMovement(null, null, null, null, false, reason);
    }
}
