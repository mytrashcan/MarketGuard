package com.marketguard.domain.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class PriceSnapshotRecentQueryTest {

    private static final Instant SINCE = Instant.parse("2026-07-31T00:30:00Z");

    @Autowired
    private PriceSnapshotRepository repository;

    @Test
    void returnsOnlyRequestedCodesSinceTheCutoffNewestFirst() {
        repository.save(snapshot("005930", SINCE.minusSeconds(1)));
        repository.save(snapshot("005930", SINCE));
        repository.save(snapshot("005930", SINCE.plusSeconds(60)));
        repository.save(snapshot("000660", SINCE.plusSeconds(30)));
        repository.save(snapshot("035720", SINCE.plusSeconds(90)));

        List<PriceSnapshot> rows = repository.findRecentForCodes(List.of("005930", "000660"), SINCE);

        assertThat(rows).extracting(PriceSnapshot::getStockCode, PriceSnapshot::getCapturedAt).containsExactly(
                org.assertj.core.groups.Tuple.tuple("005930", SINCE.plusSeconds(60)),
                org.assertj.core.groups.Tuple.tuple("000660", SINCE.plusSeconds(30)),
                org.assertj.core.groups.Tuple.tuple("005930", SINCE));
    }

    private static PriceSnapshot snapshot(String code, Instant at) {
        return new PriceSnapshot(code, new BigDecimal("1000"), at);
    }
}
