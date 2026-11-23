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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // 같은 분에 나온 신호는 같은 시장 기준을 쓰므로 한 번만 계산한다.
        Map<Instant, Optional<BigDecimal>> benchmarks = new HashMap<>();
        pending.forEach(record -> record.recordOutcome(measure(record, benchmarks), now));
        if (!pending.isEmpty()) {
            log.debug("신호 결과 {}건 측정", pending.size());
        }
        return pending.size();
    }

    SignalOutcome measure(AnomalyRecord record, Map<Instant, Optional<BigDecimal>> benchmarks) {
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
        BigDecimal market = benchmarks
                .computeIfAbsent(signalAt.truncatedTo(ChronoUnit.MINUTES), minute -> Optional.ofNullable(marketChange(minute)))
                .orElse(null);
        return calculator.measure(record.getDirection(), basePrice, outcomePrice, market);
    }

    /** minute부터 horizon 동안 스캔 종목 변동률의 중앙값. 종목마다 구간 시작 직전·끝 직후 가격을 쓴다. */
    private BigDecimal marketChange(Instant minute) {
        Instant end = minute.plus(properties.horizon());
        Map<String, BigDecimal> base = latestPerStock(
                snapshotRepository.findPricesBetween(minute.minus(properties.matchWindow()), minute));
        Map<String, BigDecimal> outcome = earliestPerStock(
                snapshotRepository.findPricesBetween(end, end.plus(properties.matchWindow())));
        return benchmark.medianChangePercent(base, outcome);
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
