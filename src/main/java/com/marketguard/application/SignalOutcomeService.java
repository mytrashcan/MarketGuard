package com.marketguard.application;

import com.marketguard.config.MaintenanceProperties;
import com.marketguard.config.OutcomeProperties;
import com.marketguard.detection.evaluation.MarketBenchmark;
import com.marketguard.detection.evaluation.SignalOutcome;
import com.marketguard.detection.evaluation.SignalOutcomeCalculator;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.marketdata.PriceSnapshot;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import com.marketguard.domain.marketdata.SnapshotPrice;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신호 이후 일정 시간(horizon) 뒤 가격을 기록한다.
 * 스냅샷은 짧게만 보존되므로, 보존 기간 안에 한 번 측정해 결과만 신호에 남긴다.
 */
@Slf4j
@Service
public class SignalOutcomeService {

    private static final Pattern STOCK_SYMBOL = Pattern.compile("\\d{6}");

    private final AnomalyRepository anomalyRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final OutcomeProperties properties;
    private final Clock clock;
    private final SignalOutcomeCalculator calculator = new SignalOutcomeCalculator();
    private final MarketBenchmark benchmark;

    public SignalOutcomeService(AnomalyRepository anomalyRepository,
                                PriceSnapshotRepository snapshotRepository,
                                OutcomeProperties properties,
                                MaintenanceProperties maintenance,
                                Clock clock) {
        if (maintenance.snapshotRetention().compareTo(properties.requiredSnapshotRetention()) <= 0) {
            throw new IllegalStateException("maintenance.snapshot-retention must be longer than "
                    + properties.requiredSnapshotRetention() + " so outcome measurement can find its prices");
        }
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.properties = properties;
        this.clock = clock;
        this.benchmark = new MarketBenchmark(properties.benchmarkMinimumSamples());
    }

    /** 측정 시점과 확인 구간이 모두 지난 신호를 측정한다. 처리한 건수를 돌려준다. */
    @Transactional
    @Scheduled(fixedDelayString = "${outcome.measure-interval:PT1M}")
    public int measurePending() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.horizon()).minus(properties.matchWindow());
        List<AnomalyRecord> pending =
                anomalyRepository.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(cutoff);
        // 같은 분에 나온 신호들의 가격 행은 한 번만 읽고, 신호마다 정확한 구간으로 다시 거른다.
        Map<Instant, MinuteRows> rows = new HashMap<>();
        pending.forEach(record -> record.recordOutcome(measure(record, rows), properties.horizon(), now));
        if (!pending.isEmpty()) {
            log.debug("신호 결과 {}건 측정", pending.size());
        }
        return pending.size();
    }

    SignalOutcome measure(AnomalyRecord record, Map<Instant, MinuteRows> rows) {
        if (!STOCK_SYMBOL.matcher(record.getStockCode()).matches()) {
            return SignalOutcome.notApplicable();
        }
        Instant signalAt = record.getDetectedAt();
        Instant target = signalAt.plus(properties.horizon());
        BigDecimal basePrice = snapshotRepository
                .findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtDesc(
                        record.getStockCode(), signalAt.minus(properties.matchWindow()), signalAt)
                .map(PriceSnapshot::getPrice).orElse(null);
        BigDecimal outcomePrice = snapshotRepository
                .findFirstByStockCodeAndCapturedAtBetweenOrderByCapturedAtAsc(
                        record.getStockCode(), target, target.plus(properties.matchWindow()))
                .map(PriceSnapshot::getPrice).orElse(null);
        if (basePrice == null || outcomePrice == null) {
            return calculator.measure(record.getDirection(), basePrice, outcomePrice);
        }
        MinuteRows minuteRows = rows.computeIfAbsent(signalAt.truncatedTo(ChronoUnit.MINUTES), this::minuteRows);
        BigDecimal market = benchmark.medianChangePercent(
                latestPerStock(within(minuteRows.base(), signalAt.minus(properties.matchWindow()), signalAt)),
                earliestPerStock(within(minuteRows.outcome(), target, target.plus(properties.matchWindow()))));
        return calculator.measure(record.getDirection(), basePrice, outcomePrice, market);
    }

    /**
     * minute로 시작하는 1분 안의 어떤 신호든 쓸 수 있도록 넉넉히 읽은 가격 행.
     * 시장 기준은 신호 종목과 같은 정확한 구간(신호 시각 기준)으로 다시 걸러 쓴다.
     */
    record MinuteRows(List<SnapshotPrice> base, List<SnapshotPrice> outcome) {
    }

    private MinuteRows minuteRows(Instant minute) {
        Instant nextMinute = minute.plus(Duration.ofMinutes(1));
        Instant outcomeStart = minute.plus(properties.horizon());
        return new MinuteRows(
                snapshotRepository.findPricesBetween(minute.minus(properties.matchWindow()), nextMinute),
                snapshotRepository.findPricesBetween(outcomeStart,
                        nextMinute.plus(properties.horizon()).plus(properties.matchWindow())));
    }

    private static List<SnapshotPrice> within(List<SnapshotPrice> prices, Instant from, Instant to) {
        return prices.stream()
                .filter(price -> !price.capturedAt().isBefore(from) && !price.capturedAt().isAfter(to))
                .toList();
    }

    private static Map<String, BigDecimal> latestPerStock(List<SnapshotPrice> prices) {
        return pricePerStock(prices, Comparator.comparing(SnapshotPrice::capturedAt));
    }

    private static Map<String, BigDecimal> earliestPerStock(List<SnapshotPrice> prices) {
        return pricePerStock(prices, Comparator.comparing(SnapshotPrice::capturedAt).reversed());
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
