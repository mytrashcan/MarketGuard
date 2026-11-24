package com.marketguard.dashboard;

import com.marketguard.detection.model.DecimalMath;
import com.marketguard.domain.anomaly.PricePathRow;
import com.marketguard.domain.anomaly.SignalPricePointRepository;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * 신호 가격 경로를 CSV로 내보낸다. 한 줄이 (신호, 신호 후 시점) 하나다.
 * 판정 없이 원값만 내보내므로, 측정 시점·임계값·기준을 바꿔 가며 밖에서 다시 분석할 수 있다.
 */
@Service
public class PricePathExport {

    static final String HEADER = "anomaly_id,detected_at,stock_code,rule_type,severity,direction,"
            + "observed_value,threshold_value,deviation_ratio,offset_minutes,base_price,price,"
            + "change_percent,market_change_percent,sector_change_percent\n";

    /** 한 번에 읽는 행 수. 내보내기 기간이 길어도 메모리에는 이만큼만 올라간다. */
    static final int BATCH_SIZE = 2_000;

    private final SignalPricePointRepository repository;
    private final Clock clock;
    private final int batchSize;

    @Autowired
    public PricePathExport(SignalPricePointRepository repository, Clock clock) {
        this(repository, clock, BATCH_SIZE);
    }

    PricePathExport(SignalPricePointRepository repository, Clock clock, int batchSize) {
        this.repository = repository;
        this.clock = clock;
        this.batchSize = batchSize;
    }

    /**
     * 최근 days일(KST, 오늘 포함)에 나온 신호의 가격 경로를 out에 쓴다. batchSize(기본 BATCH_SIZE)씩 이어 읽으며 바로 쓰므로
     * 전체를 메모리에 모으지 않는다. 배치마다 따로 읽으므로, 쓰는 도중 기록된 점은 키 순서상 뒤에 있으면 포함된다.
     */
    public void write(int days, Writer out) throws IOException {
        Instant now = clock.instant();
        LocalDate fromDay = LocalDate.ofInstant(now, SignalAnalyticsService.KST).minusDays(days - 1L);
        Instant from = fromDay.atStartOfDay(SignalAnalyticsService.KST).toInstant();
        out.write(HEADER);
        // 첫 배치는 from 직전을 기준으로 삼아 from 이후 전부를 읽는다
        Instant afterAt = from.minusNanos(1);
        long afterId = 0;
        int afterOffset = 0;
        List<PricePathRow> batch;
        do {
            batch = repository.findPathRowsAfter(from, now, afterAt, afterId, afterOffset, Limit.of(batchSize));
            for (PricePathRow row : batch) {
                out.write(line(row));
                out.write('\n');
            }
            if (!batch.isEmpty()) {
                PricePathRow last = batch.get(batch.size() - 1);
                afterAt = last.detectedAt();
                afterId = last.anomalyId();
                afterOffset = last.offsetMinutes();
            }
        } while (batch.size() == batchSize);
        out.flush();
    }

    private static String line(PricePathRow row) {
        return String.join(",",
                String.valueOf(row.anomalyId()),
                row.detectedAt().toString(),
                text(row.stockCode()),
                String.valueOf(row.ruleType()),
                String.valueOf(row.severity()),
                row.direction() == null ? "" : row.direction().name(),
                number(row.observedValue()),
                number(row.thresholdValue()),
                number(row.deviationRatio()),
                String.valueOf(row.offsetMinutes()),
                number(row.basePrice()),
                number(row.price()),
                number(changePercent(row.basePrice(), row.price())),
                number(row.marketChangePercent()),
                number(row.sectorChangePercent()));
    }

    private static BigDecimal changePercent(BigDecimal base, BigDecimal price) {
        if (base == null || price == null) {
            return null;
        }
        return DecimalMath.percentageChange(price, base);
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /**
     * 종목 코드는 외부 API 값이므로 그대로 쓰지 않는다. 쉼표·따옴표·줄바꿈은 따옴표로 감싸고,
     * 스프레드시트가 수식으로 읽을 수 있는 첫 글자(=, +, -, @ 등)는 작은따옴표로 무력화한다.
     */
    static String text(String value) {
        String safe = Objects.requireNonNullElse(value, "");
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
