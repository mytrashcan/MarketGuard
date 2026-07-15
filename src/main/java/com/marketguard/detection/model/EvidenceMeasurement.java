package com.marketguard.detection.model;

import java.math.BigDecimal;

/** 탐지 결과를 재현하고 설명하는 개별 원시 측정값. */
public record EvidenceMeasurement(
        String key,
        String label,
        BigDecimal observedValue,
        BigDecimal baselineValue,
        BigDecimal thresholdValue,
        BigDecimal deviationRatio,
        String unit
) {
    public EvidenceMeasurement {
        requireText(key, "key", 50);
        requireText(label, "label", 100);
        requireText(unit, "unit", 30);
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
    }
}
