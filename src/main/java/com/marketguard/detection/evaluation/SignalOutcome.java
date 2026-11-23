package com.marketguard.detection.evaluation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 신호 한 건의 사후 결과.
 *
 * @param changePercent        신호 시점 가격 대비 측정 시점 가격의 변동률(%)
 * @param followedDirection    신호 방향과 같은 쪽으로 움직였는지. 방향이 없는 신호나 변동이 0이면 null
 * @param marketChangePercent  같은 구간 시장 기준 변동률(스캔 종목 변동률의 중앙값). 표본이 부족하면 null
 * @param excessChangePercent  changePercent - marketChangePercent. 시장 기준이 없으면 null
 * @param followedAfterMarket  시장 변동을 뺀 초과 변동이 신호 방향과 같은지. 판단할 수 없으면 null
 * @param sectorChangePercent  같은 구간 같은 업종 다른 종목 변동률의 중앙값. 업종 정보나 표본이 없으면 null
 * @param followedAfterSector  업종 변동을 뺀 초과 변동이 신호 방향과 같은지. 판단할 수 없으면 null
 */
public record SignalOutcome(
        OutcomeStatus status,
        BigDecimal basePrice,
        BigDecimal outcomePrice,
        BigDecimal changePercent,
        Boolean followedDirection,
        BigDecimal marketChangePercent,
        BigDecimal excessChangePercent,
        Boolean followedAfterMarket,
        BigDecimal sectorChangePercent,
        Boolean followedAfterSector
) {
    public SignalOutcome {
        Objects.requireNonNull(status, "status must not be null");
        if (status == OutcomeStatus.MEASURED && (basePrice == null || outcomePrice == null || changePercent == null)) {
            throw new IllegalArgumentException("a measured outcome needs both prices and the change");
        }
    }

    public static SignalOutcome noPriceData() {
        return new SignalOutcome(OutcomeStatus.NO_PRICE_DATA, null, null, null, null, null, null, null, null, null);
    }

    public static SignalOutcome notApplicable() {
        return new SignalOutcome(OutcomeStatus.NOT_APPLICABLE, null, null, null, null, null, null, null, null, null);
    }
}
