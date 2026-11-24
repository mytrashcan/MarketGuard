package com.marketguard.detection.evaluation;

import com.marketguard.detection.model.DecimalMath;
import com.marketguard.detection.model.Direction;
import java.math.BigDecimal;

/** 신호 시점과 일정 시간 뒤의 가격으로 결과를 계산한다. 시장 기준 변동이 있으면 초과 변동도 계산한다. */
public final class SignalOutcomeCalculator {

    public SignalOutcome measure(Direction direction, BigDecimal basePrice, BigDecimal outcomePrice) {
        return measure(direction, basePrice, outcomePrice, null, null);
    }

    /** 시장·업종 기준 변동은 없으면 null로 넘긴다. 없는 기준에 대한 판단은 null로 남는다. */
    public SignalOutcome measure(Direction direction, BigDecimal basePrice, BigDecimal outcomePrice,
                                 BigDecimal marketChangePercent, BigDecimal sectorChangePercent) {
        if (basePrice == null || outcomePrice == null || basePrice.signum() <= 0 || outcomePrice.signum() <= 0) {
            return SignalOutcome.noPriceData();
        }
        BigDecimal change = DecimalMath.percentageChange(outcomePrice, basePrice);
        BigDecimal excess = marketChangePercent == null ? null : change.subtract(marketChangePercent);
        BigDecimal sectorExcess = sectorChangePercent == null ? null : change.subtract(sectorChangePercent);
        return new SignalOutcome(OutcomeStatus.MEASURED, basePrice, outcomePrice, change,
                followed(direction, change), marketChangePercent, excess,
                excess == null ? null : followed(direction, excess),
                sectorChangePercent, sectorExcess == null ? null : followed(direction, sectorExcess));
    }

    /** 신호가 예상한 가격 방향. 매수 우위·순매수는 상승, 매도 우위·순매도는 하락으로 본다. 없으면 0. */
    static int expectedSign(Direction direction) {
        if (direction == null) {
            return 0;
        }
        return switch (direction) {
            case UP, BUY -> 1;
            case DOWN, SELL -> -1;
            case NONE -> 0;
        };
    }

    private static Boolean followed(Direction direction, BigDecimal change) {
        int expected = expectedSign(direction);
        if (expected == 0 || change.signum() == 0) {
            return null;
        }
        return change.signum() == expected;
    }
}
