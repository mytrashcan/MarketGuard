package com.marketguard.application;

import com.marketguard.application.port.SectorLookup;
import com.marketguard.config.MaintenanceProperties;
import com.marketguard.config.OutcomeProperties;
import com.marketguard.domain.anomaly.AnomalyRecord;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.anomaly.SignalPricePoint;
import com.marketguard.domain.anomaly.SignalPricePointRepository;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종목 신호마다 신호 후 여러 시점(outcome.path-offsets)의 가격을 영구 기록한다.
 *
 * <p>스냅샷은 몇 시간 안에 지워지므로, 이 기록이 없으면 측정 시점이나 임계값을 바꿔 과거 신호를
 * 다시 평가할 수 없다. 시점마다 기준 가격, 그 시점 가격, 같은 구간의 시장·업종 변동률 중앙값을
 * 원값 그대로 남기고 판정(방향 지속 여부)은 분석하는 쪽에 맡긴다.
 *
 * <p>스냅샷이 이미 지워진 신호(서비스 중단 동안 나온 신호 등)는 기록하지 않는다. 추정으로 채우지 않는다.
 */
@Slf4j
@Service
public class PricePathRecorder {

    /** 한 번에 한 시점당 처리할 최대 신호 수. 밀린 신호는 다음 주기에 이어서 처리한다. */
    static final int BATCH_SIZE = 500;

    private final AnomalyRepository anomalyRepository;
    private final PriceSnapshotRepository snapshotRepository;
    private final SignalPricePointRepository pointRepository;
    private final OutcomeProperties properties;
    private final Duration snapshotRetention;
    private final BenchmarkMoves benchmarks;
    private final Clock clock;

    public PricePathRecorder(AnomalyRepository anomalyRepository,
                             PriceSnapshotRepository snapshotRepository,
                             SignalPricePointRepository pointRepository,
                             OutcomeProperties properties,
                             MaintenanceProperties maintenance,
                             SectorLookup sectors,
                             Clock clock) {
        if (maintenance.snapshotRetention().compareTo(properties.requiredSnapshotRetention()) <= 0) {
            throw new IllegalStateException("maintenance.snapshot-retention must be longer than "
                    + properties.requiredSnapshotRetention() + " so every price path point can be recorded");
        }
        this.anomalyRepository = anomalyRepository;
        this.snapshotRepository = snapshotRepository;
        this.pointRepository = pointRepository;
        this.properties = properties;
        this.snapshotRetention = maintenance.snapshotRetention();
        this.benchmarks = new BenchmarkMoves(
                sectors, properties.benchmarkMinimumSamples(), properties.sectorMinimumSamples());
        this.clock = clock;
    }

    /** 확인 구간까지 지난 시점들을 기록한다. 기록한 점 수를 돌려준다. */
    @Transactional
    @Scheduled(fixedDelayString = "${outcome.measure-interval:PT1M}")
    public int recordDue() {
        if (properties.pathOffsets().isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        // 신호 시각의 기준 가격 구간이 아직 지워지지 않은 신호만 본다.
        Instant earliestSignal = now.minus(snapshotRetention).plus(properties.matchWindow());
        SnapshotWindows windows = new SnapshotWindows(snapshotRepository, properties.matchWindow());
        List<SignalPricePoint> points = new ArrayList<>();
        for (Duration offset : properties.pathOffsets()) {
            // 이 시점의 확인 구간까지 끝난 신호만. 보존 기간 검사 덕분에 범위는 비어 있지 않다.
            Instant latestSignal = now.minus(offset).minus(properties.matchWindow());
            int minutes = Math.toIntExact(offset.toMinutes());
            anomalyRepository.findMissingPricePoint(earliestSignal, latestSignal, minutes, Limit.of(BATCH_SIZE))
                    .forEach(record -> points.add(point(record, offset, windows, now)));
        }
        pointRepository.saveAll(points);
        if (!points.isEmpty()) {
            log.debug("신호 가격 경로 {}점 기록", points.size());
        }
        return points.size();
    }

    private SignalPricePoint point(AnomalyRecord record, Duration offset, SnapshotWindows windows, Instant now) {
        Instant signalAt = record.getDetectedAt();
        Map<String, BigDecimal> basePrices = windows.latestUpTo(signalAt);
        Map<String, BigDecimal> laterPrices = windows.earliestFrom(signalAt.plus(offset));
        BenchmarkMoves.Moves moves = benchmarks.between(record.getStockCode(), basePrices, laterPrices);
        return new SignalPricePoint(record.getId(), Math.toIntExact(offset.toMinutes()),
                basePrices.get(record.getStockCode()), laterPrices.get(record.getStockCode()),
                moves.market(), moves.sector(), now);
    }
}
