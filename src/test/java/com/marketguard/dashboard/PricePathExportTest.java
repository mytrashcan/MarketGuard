package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.PricePathRow;
import com.marketguard.domain.anomaly.SignalPricePointRepository;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;

class PricePathExportTest {

    /** 2026-07-31 10:00 KST */
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");
    private static final Instant SIGNAL_AT = Instant.parse("2026-07-31T00:30:00Z");
    /** 2026-07-31 00:00 KST */
    private static final Instant TODAY = Instant.parse("2026-07-30T15:00:00Z");

    private final SignalPricePointRepository repository = mock(SignalPricePointRepository.class);

    private String export(int days, int batchSize) throws IOException {
        StringWriter out = new StringWriter();
        new PricePathExport(repository, Clock.fixed(NOW, ZoneOffset.UTC), batchSize).write(days, out);
        return out.toString();
    }

    @Test
    void writesOneLinePerPointWithTheDerivedChange() throws IOException {
        when(repository.findPathRowsAfter(any(), any(), any(), anyLong(), anyInt(), any())).thenReturn(List.of(
                row(5, new BigDecimal("71400.0000"), new BigDecimal("0.50000000")),
                row(15, null, null)));

        String csv = export(1, 10);

        assertThat(csv.lines().toList()).containsExactly(
                PricePathExport.HEADER.strip(),
                "7,2026-07-31T00:30:00Z,005930,PRICE_SPIKE,WARNING,UP,4,3,1.333,5,70000,71400,2,0.5,",
                "7,2026-07-31T00:30:00Z,005930,PRICE_SPIKE,WARNING,UP,4,3,1.333,15,70000,,,,");
    }

    @Test
    void writesASignalWithoutPointsAsOneLineWithEmptyPointColumns() throws IOException {
        when(repository.findPathRowsAfter(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(List.of(row(null, null, null)));

        assertThat(export(1, 10).lines().skip(1).toList())
                .containsExactly("7,2026-07-31T00:30:00Z,005930,PRICE_SPIKE,WARNING,UP,4,3,1.333,,,,,,");
    }

    @Test
    void readsInBatchesContinuingAfterTheLastRowsKey() throws IOException {
        Instant from = Instant.parse("2026-07-29T15:00:00Z"); // 2026-07-30 00:00 KST
        when(repository.findPathRowsAfter(from, NOW, from.minusNanos(1), 0, 0, Limit.of(2)))
                .thenReturn(List.of(row(5, null, null), row(15, null, null)));
        when(repository.findPathRowsAfter(from, NOW, SIGNAL_AT, 7, 15, Limit.of(2)))
                .thenReturn(List.of(row(30, null, null)));

        String csv = export(2, 2);

        assertThat(csv.lines().skip(1).map(line -> line.split(",")[9]).toList()).containsExactly("5", "15", "30");
        // 마지막 배치가 batchSize보다 작으면 더 읽지 않는다
        verify(repository, times(2)).findPathRowsAfter(any(), any(), any(), anyLong(), anyInt(), any());
    }

    @Test
    void writesOnlyTheHeaderWhenThereIsNothingToExport() throws IOException {
        when(repository.findPathRowsAfter(any(), any(), any(), anyLong(), anyInt(), any())).thenReturn(List.of());

        assertThat(export(1, 10)).isEqualTo(PricePathExport.HEADER);
        verify(repository).findPathRowsAfter(TODAY, NOW, TODAY.minusNanos(1), 0, 0, Limit.of(10));
    }

    @Test
    void neutralizesSpreadsheetFormulasAndQuotesSeparators() {
        assertThat(PricePathExport.text("005930")).isEqualTo("005930");
        assertThat(PricePathExport.text("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(PricePathExport.text("a,b\"c")).isEqualTo("\"a,b\"\"c\"");
    }

    private static PricePathRow row(Integer offset, BigDecimal price, BigDecimal market) {
        return new PricePathRow(7L, SIGNAL_AT, "005930", RuleType.PRICE_SPIKE,
                Severity.WARNING, Direction.UP, new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("1.333"),
                offset, offset == null ? null : new BigDecimal("70000.0000"), price, market, null);
    }
}
