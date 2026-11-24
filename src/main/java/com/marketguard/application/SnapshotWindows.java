package com.marketguard.application;

import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.domain.marketdata.SnapshotPrice;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 측정 작업 안에서 쓰는 종목별 시점 가격 조회. 같은 분에 걸친 시점들은 가격 행을 한 번만 읽고,
 * 시점마다 정확한 구간으로 다시 거른다. 작업마다 새로 만들어 쓴다(캐시가 작업 범위를 넘지 않도록).
 */
final class SnapshotWindows {

    private final PriceSnapshotRepository repository;
    private final Duration matchWindow;
    private final Map<Instant, List<SnapshotPrice>> beforeRows = new HashMap<>();
    private final Map<Instant, List<SnapshotPrice>> afterRows = new HashMap<>();

    SnapshotWindows(PriceSnapshotRepository repository, Duration matchWindow) {
        this.repository = repository;
        this.matchWindow = matchWindow;
    }

    /** [at - matchWindow, at] 안에서 종목별 가장 늦은 가격. 신호 시점의 기준 가격으로 쓴다. */
    Map<String, BigDecimal> latestUpTo(Instant at) {
        List<SnapshotPrice> rows = beforeRows.computeIfAbsent(minuteOf(at), minute ->
                repository.findPricesBetween(minute.minus(matchWindow), minute.plus(Duration.ofMinutes(1))));
        return pricePerStock(within(rows, at.minus(matchWindow), at),
                Comparator.comparing(SnapshotPrice::capturedAt));
    }

    /** [at, at + matchWindow] 안에서 종목별 가장 이른 가격. 측정 시점의 가격으로 쓴다. */
    Map<String, BigDecimal> earliestFrom(Instant at) {
        List<SnapshotPrice> rows = afterRows.computeIfAbsent(minuteOf(at), minute ->
                repository.findPricesBetween(minute, minute.plus(Duration.ofMinutes(1)).plus(matchWindow)));
        return pricePerStock(within(rows, at, at.plus(matchWindow)),
                Comparator.comparing(SnapshotPrice::capturedAt).reversed());
    }

    private static Instant minuteOf(Instant at) {
        return at.truncatedTo(ChronoUnit.MINUTES);
    }

    private static List<SnapshotPrice> within(List<SnapshotPrice> prices, Instant from, Instant to) {
        return prices.stream()
                .filter(price -> !price.capturedAt().isBefore(from) && !price.capturedAt().isAfter(to))
                .toList();
    }

    /** later가 가리키는 쪽의 가격을 종목별로 남긴다. */
    private static Map<String, BigDecimal> pricePerStock(List<SnapshotPrice> prices, Comparator<SnapshotPrice> later) {
        Map<String, SnapshotPrice> chosen = new HashMap<>();
        prices.forEach(price -> chosen.merge(price.stockCode(), price,
                (current, candidate) -> later.compare(candidate, current) > 0 ? candidate : current));
        Map<String, BigDecimal> result = new HashMap<>();
        chosen.forEach((code, price) -> result.put(code, price.price()));
        return result;
    }
}
