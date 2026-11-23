package com.marketguard.application;

import com.marketguard.application.port.SectorLookup;
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
    private final MarketBenchmark sectorBenchmark;
    private final SectorLookup sectors;

    public SignalOutcomeService(AnomalyRepository anomalyRepository,
                                PriceSnapshotRepository snapshotRepository,
                                OutcomeProperties properties,
                                MaintenanceProperties maintenance,
                                SectorLookup sectors,
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
        this.sectorBenchmark = new MarketBenchmark(properties.sectorMinimumSamples());
        this.sectors = sectors;
    }

    /** 측정 시점과 확인 구간이 모두 지난 신호를 측정한다. 처리한 건수를 돌려준다. */
    @Transactional
    @Scheduled(fixedDelayString = "${outcome.measure-interval:PT1M}")
    public int measurePending() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(properties.horizon()).minus(properties.matchWindow());
        List<AnomalyRecord> pending =
                anomalyRepository.findTop200ByOutcomeStatusIsNullAndDetectedAtBeforeOrderByDetectedAtAsc(cutoff);
        // 같은 분에 나온 신호는 같은 구간 가격을 쓰므로 한 번만 읽는다.
        Map<Instant, WindowPrices> windows = new HashMap<>();
        pending.forEach(record -> record.recordOutcome(measure(record, windows), properties.horizon(), now));
        if (!pending.isEmpty()) {
            log.debug("신호 결과 {}건 측정", pending.size());
        }
        return pending.size();
    }

    SignalOutcome measure(AnomalyRecord record, Map<Instant, WindowPrices> windows) {
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
        WindowPrices window = windows.computeIfAbsent(signalAt.truncatedTo(ChronoUnit.MINUTES), this::windowPrices);
        BigDecimal market = benchmark.medianChangePercent(window.base(), window.outcome());
        BigDecimal sector = sectors.sectorOf(record.getStockCode())
                .map(name -> sectorChange(window, name, record.getStockCode()))
                .orElse(null);
        return calculator.measure(record.getDirection(), basePrice, outcomePrice, market, sector);
    }

    /** 신호 종목을 뺀 같은 업종 종목의 변동률 중앙값. */
    private BigDecimal sectorChange(WindowPrices window, String sector, String excludedCode) {
        Map<String, BigDecimal> base = new HashMap<>();
        window.base().forEach((code, price) -> {
            if (!code.equals(excludedCode) && sectors.sectorOf(code).filter(sector::equals).isPresent()) {
                base.put(code, price);
            }
        });
        return sectorBenchmark.medianChangePercent(base, window.outcome());
    }

    /** 종목별 구간 시작 직전 가격과 끝 직후 가격. */
    record WindowPrices(Map<String, BigDecimal> base, Map<String, BigDecimal> outcome) {
    }

    private WindowPrices windowPrices(Instant minute) {
        Instant end = minute.plus(properties.horizon());
        return new WindowPrices(
                latestPerStock(snapshotRepository.findPricesBetween(minute.minus(properties.matchWindow()), minute)),
                earliestPerStock(snapshotRepository.findPricesBetween(end, end.plus(properties.matchWindow()))));
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
