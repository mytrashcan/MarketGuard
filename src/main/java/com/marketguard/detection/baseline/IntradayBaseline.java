package com.marketguard.detection.baseline;

import java.math.BigDecimal;
import java.time.LocalTime;

public record IntradayBaseline(
        LocalTime bucketStart,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal standardDeviation,
        int sampleCount,
        BaselineSource source
) {
    public boolean available() {
        return source != BaselineSource.INSUFFICIENT_DATA;
    }
}
