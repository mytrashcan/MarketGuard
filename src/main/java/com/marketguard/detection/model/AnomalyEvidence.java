package com.marketguard.detection.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 문자열 메시지와 분리해 보존하는 설명 가능한 탐지 근거.
 * 값이 적용되지 않는 규칙은 nullable 숫자 필드를 비워 두되 설명과 확인 항목은 항상 제공한다.
 */
public record AnomalyEvidence(
        String title,
        String summary,
        String explanation,
        BigDecimal observedValue,
        BigDecimal baselineValue,
        BigDecimal thresholdValue,
        BigDecimal deviationRatio,
        String valueUnit,
        String lookbackDescription,
        Direction direction,
        List<String> contextTags,
        List<String> recommendedChecks,
        Instant marketObservedAt,
        String caution,
        List<EvidenceMeasurement> measurements
) {
    public static final String DEFAULT_CAUTION =
            "이 신호만으로 불공정거래, 시세조종, 투자 위험 또는 투자 가치를 판단할 수 없습니다.";

    public AnomalyEvidence {
        requireText(title, "title", 120);
        requireText(summary, "summary", 500);
        requireText(explanation, "explanation", 2_000);
        if (valueUnit != null && valueUnit.length() > 30) {
            throw new IllegalArgumentException("valueUnit must not exceed 30 characters");
        }
        if (lookbackDescription != null && lookbackDescription.length() > 200) {
            throw new IllegalArgumentException("lookbackDescription must not exceed 200 characters");
        }
        direction = direction == null ? Direction.NONE : direction;
        contextTags = copyValidated(contextTags, "contextTags", 20, 80);
        recommendedChecks = copyValidated(recommendedChecks, "recommendedChecks", 20, 300);
        requireText(caution, "caution", 500);
        measurements = measurements == null ? List.of() : List.copyOf(measurements);
        if (measurements.size() > 20) {
            throw new IllegalArgumentException("measurements must not contain more than 20 entries");
        }
    }

    public static Builder builder(String title, String summary, String explanation) {
        return new Builder(title, summary, explanation);
    }

    public static AnomalyEvidence legacy(String message, Instant observedAt) {
        return builder("기존 탐지 신호", message,
                "이 기록은 구조화된 근거 모델 도입 전에 생성되어 원시 비교값이 남아 있지 않습니다.")
                .marketObservedAt(observedAt)
                .recommendedChecks(List.of("현재 시장 데이터와 탐지 규칙을 다시 확인하세요."))
                .build();
    }

    private static List<String> copyValidated(List<String> values, String name, int maxItems, int maxLength) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > maxItems) {
            throw new IllegalArgumentException(name + " must not contain more than " + maxItems + " entries");
        }
        return values.stream().map(value -> {
            requireText(value, name + " entry", maxLength);
            return value;
        }).distinct().toList();
    }

    private static void requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
    }

    public static final class Builder {
        private final String title;
        private final String summary;
        private final String explanation;
        private BigDecimal observedValue;
        private BigDecimal baselineValue;
        private BigDecimal thresholdValue;
        private BigDecimal deviationRatio;
        private String valueUnit;
        private String lookbackDescription;
        private Direction direction = Direction.NONE;
        private List<String> contextTags = List.of();
        private List<String> recommendedChecks = List.of();
        private Instant marketObservedAt;
        private String caution = DEFAULT_CAUTION;
        private List<EvidenceMeasurement> measurements = List.of();

        private Builder(String title, String summary, String explanation) {
            this.title = title;
            this.summary = summary;
            this.explanation = explanation;
        }

        public Builder values(BigDecimal observed, BigDecimal baseline, BigDecimal threshold,
                              BigDecimal deviation, String unit) {
            this.observedValue = observed;
            this.baselineValue = baseline;
            this.thresholdValue = threshold;
            this.deviationRatio = deviation;
            this.valueUnit = unit;
            return this;
        }

        public Builder lookback(String value) {
            this.lookbackDescription = value;
            return this;
        }

        public Builder direction(Direction value) {
            this.direction = Objects.requireNonNull(value, "direction must not be null");
            return this;
        }

        public Builder contextTags(List<String> values) {
            this.contextTags = values;
            return this;
        }

        public Builder recommendedChecks(List<String> values) {
            this.recommendedChecks = values;
            return this;
        }

        public Builder marketObservedAt(Instant value) {
            this.marketObservedAt = value;
            return this;
        }

        public Builder caution(String value) {
            this.caution = value;
            return this;
        }

        public Builder measurements(List<EvidenceMeasurement> values) {
            this.measurements = values;
            return this;
        }

        public AnomalyEvidence build() {
            return new AnomalyEvidence(title, summary, explanation, observedValue, baselineValue,
                    thresholdValue, deviationRatio, valueUnit, lookbackDescription, direction,
                    contextTags, recommendedChecks, marketObservedAt, caution, measurements);
        }
    }
}
