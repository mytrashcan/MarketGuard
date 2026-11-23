package com.marketguard.dashboard;

import com.marketguard.config.OutcomeProperties;
import com.marketguard.detection.evaluation.OutcomeStatistics;
import com.marketguard.detection.evaluation.OutcomeStatus;
import com.marketguard.detection.evaluation.SignalOutcome;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.anomaly.SignalStatRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 신호 수와 사후 결과를 기간별로 집계한다. 날짜는 한국 거래일 기준(KST)으로 나눈다. */
@Service
@Transactional(readOnly = true)
public class SignalAnalyticsService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AnomalyRepository anomalyRepository;
    private final OutcomeProperties outcomeProperties;
    private final Clock clock;

    public SignalAnalyticsService(AnomalyRepository anomalyRepository,
                                  OutcomeProperties outcomeProperties,
                                  Clock clock) {
        this.anomalyRepository = anomalyRepository;
        this.outcomeProperties = outcomeProperties;
        this.clock = clock;
    }

    public SignalAnalyticsView summarize(int days) {
        Instant now = clock.instant();
        LocalDate to = LocalDate.ofInstant(now, KST);
        LocalDate from = to.minusDays(days - 1L);
        List<SignalStatRow> rows = anomalyRepository.findStatRows(from.atStartOfDay(KST).toInstant(), now);
        return new SignalAnalyticsView(days, from, to, outcomeProperties.horizon().toMinutes(), rows.size(),
                daily(rows, from, to), rules(rows), coverage(rows));
    }

    private static List<SignalAnalyticsView.Day> daily(List<SignalStatRow> rows, LocalDate from, LocalDate to) {
        Map<LocalDate, List<SignalStatRow>> byDate = rows.stream()
                .collect(Collectors.groupingBy(row -> LocalDate.ofInstant(row.detectedAt(), KST)));
        List<SignalAnalyticsView.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<SignalStatRow> day = byDate.getOrDefault(date, List.of());
            days.add(new SignalAnalyticsView.Day(date, day.size(),
                    day.stream().filter(row -> row.severity() == Severity.CRITICAL).count(),
                    day.stream().map(SignalStatRow::stockCode).distinct().count()));
        }
        return days;
    }

    private static List<SignalAnalyticsView.Rule> rules(List<SignalStatRow> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(SignalStatRow::ruleType))
                .entrySet().stream()
                .map(entry -> {
                    OutcomeStatistics statistics = OutcomeStatistics.of(
                            entry.getValue().stream().map(SignalStatRow::outcome).toList());
                    return new SignalAnalyticsView.Rule(entry.getKey(), statistics.signals(), statistics.measured(),
                            statistics.directional(), statistics.followedRatePercent(),
                            statistics.medianAbsChangePercent());
                })
                .sorted(Comparator.comparingLong(SignalAnalyticsView.Rule::signals).reversed()
                        .thenComparing(SignalAnalyticsView.Rule::ruleType))
                .toList();
    }

    private static SignalAnalyticsView.Coverage coverage(List<SignalStatRow> rows) {
        Map<OutcomeStatus, Long> counts = rows.stream()
                .map(SignalStatRow::outcome)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(SignalOutcome::status, Collectors.counting()));
        long pending = rows.stream().map(SignalStatRow::outcome).filter(Objects::isNull).count();
        Function<OutcomeStatus, Long> count = status -> counts.getOrDefault(status, 0L);
        return new SignalAnalyticsView.Coverage(count.apply(OutcomeStatus.MEASURED),
                count.apply(OutcomeStatus.NO_PRICE_DATA), count.apply(OutcomeStatus.NOT_APPLICABLE), pending);
    }
}
