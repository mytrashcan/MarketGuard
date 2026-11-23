package com.marketguard.detection.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketguard.detection.model.Direction;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalOutcomeCalculatorTest {

    private final SignalOutcomeCalculator calculator = new SignalOutcomeCalculator();

    @Test
    void measuresChangeAndWhetherThePriceKeptTheSignalDirection() {
        SignalOutcome up = calculator.measure(Direction.UP, new BigDecimal("10000"), new BigDecimal("10150"));
        SignalOutcome reversed = calculator.measure(Direction.SELL, new BigDecimal("10000"), new BigDecimal("10100"));

        assertThat(up.status()).isEqualTo(OutcomeStatus.MEASURED);
        assertThat(up.changePercent()).isEqualByComparingTo("1.5");
        assertThat(up.followedDirection()).isTrue();
        assertThat(reversed.followedDirection()).isFalse();
    }

    @Test
    void leavesDirectionUndecidedWithoutAnExpectedDirectionOrMovement() {
        assertThat(calculator.measure(Direction.NONE, new BigDecimal("100"), new BigDecimal("101")).followedDirection())
                .isNull();
        assertThat(calculator.measure(Direction.UP, new BigDecimal("100"), new BigDecimal("100")).followedDirection())
                .isNull();
    }

    @Test
    void reportsMissingPricesInsteadOfInventingAResult() {
        assertThat(calculator.measure(Direction.UP, null, new BigDecimal("100")).status())
                .isEqualTo(OutcomeStatus.NO_PRICE_DATA);
        assertThat(calculator.measure(Direction.UP, new BigDecimal("100"), BigDecimal.ZERO).status())
                .isEqualTo(OutcomeStatus.NO_PRICE_DATA);
    }

    @Test
    void summarizesFollowRateAndMedianOnlyOverMeasuredSignals() {
        List<SignalOutcome> outcomes = Arrays.asList(
                calculator.measure(Direction.UP, new BigDecimal("100"), new BigDecimal("102")),
                calculator.measure(Direction.UP, new BigDecimal("100"), new BigDecimal("99")),
                calculator.measure(Direction.DOWN, new BigDecimal("100"), new BigDecimal("97")),
                calculator.measure(Direction.NONE, new BigDecimal("100"), new BigDecimal("104")),
                SignalOutcome.noPriceData(),
                null);

        OutcomeStatistics statistics = OutcomeStatistics.of(outcomes);

        assertThat(statistics.signals()).isEqualTo(6);
        assertThat(statistics.measured()).isEqualTo(4);
        assertThat(statistics.followed().samples()).isEqualTo(3);
        assertThat(statistics.followed().ratePercent()).isEqualByComparingTo("66.67");
        assertThat(statistics.medianAbsChangePercent()).isEqualByComparingTo("2.50");
    }

    @Test
    void returnsNoRatesWhenNothingWasMeasured() {
        OutcomeStatistics statistics = OutcomeStatistics.of(List.of(SignalOutcome.noPriceData()));

        assertThat(statistics.followed()).isNull();
        assertThat(statistics.medianAbsChangePercent()).isNull();
    }
}
