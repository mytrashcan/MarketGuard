package com.marketguard.detection.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketguard.detection.model.Direction;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MarketBenchmarkTest {

    @Test
    void usesTheMedianChangeOfStocksWithBothPrices() {
        MarketBenchmark benchmark = new MarketBenchmark(3);

        BigDecimal median = benchmark.medianChangePercent(
                Map.of("A", new BigDecimal("100"), "B", new BigDecimal("100"), "C", new BigDecimal("100"),
                        "D", new BigDecimal("100")),
                Map.of("A", new BigDecimal("101"), "B", new BigDecimal("99"), "C", new BigDecimal("110")));

        assertThat(median).isEqualByComparingTo("1");
    }

    @Test
    void returnsNoBenchmarkBelowTheMinimumSample() {
        MarketBenchmark benchmark = new MarketBenchmark(3);

        assertThat(benchmark.medianChangePercent(
                Map.of("A", new BigDecimal("100"), "B", new BigDecimal("100")),
                Map.of("A", new BigDecimal("101"), "B", new BigDecimal("102")))).isNull();
        assertThatThrownBy(() -> new MarketBenchmark(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void judgesDirectionOnTheMoveBeyondTheMarket() {
        SignalOutcomeCalculator calculator = new SignalOutcomeCalculator();

        // 종목 +1%, 시장 +2% → 초과 -1%: 겉보기엔 상승 지속이지만 시장보다 약했다.
        SignalOutcome rodeTheMarket = calculator.measure(
                Direction.UP, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("2"), null);
        SignalOutcome withoutBenchmark = calculator.measure(
                Direction.UP, new BigDecimal("100"), new BigDecimal("101"), null, new BigDecimal("0.5"));

        assertThat(rodeTheMarket.followedDirection()).isTrue();
        assertThat(rodeTheMarket.excessChangePercent()).isEqualByComparingTo("-1");
        assertThat(rodeTheMarket.followedAfterMarket()).isFalse();
        assertThat(withoutBenchmark.excessChangePercent()).isNull();
        assertThat(withoutBenchmark.followedAfterMarket()).isNull();
        // 업종 +0.5% → 업종 대비 +0.5%: 업종보다는 강했다.
        assertThat(withoutBenchmark.sectorChangePercent()).isEqualByComparingTo("0.5");
        assertThat(withoutBenchmark.followedAfterSector()).isTrue();
    }
}
