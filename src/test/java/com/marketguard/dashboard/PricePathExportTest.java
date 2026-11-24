package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.detection.model.Direction;
import com.marketguard.detection.model.RuleType;
import com.marketguard.detection.model.Severity;
import com.marketguard.domain.anomaly.PricePathRow;
import com.marketguard.domain.anomaly.SignalPricePointRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PricePathExportTest {

    /** 2026-07-31 10:00 KST */
    private static final Instant NOW = Instant.parse("2026-07-31T01:00:00Z");

    private final SignalPricePointRepository repository = mock(SignalPricePointRepository.class);
    private final PricePathExport export = new PricePathExport(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void writesOneLinePerPointWithTheDerivedChange() {
        when(repository.findPathRows(any(), any())).thenReturn(List.of(
                row(new BigDecimal("70000.0000"), new BigDecimal("71400.0000"), new BigDecimal("0.50000000")),
                row(new BigDecimal("70000.0000"), null, null)));

        String csv = export.csv(1);

        assertThat(csv.lines().toList()).containsExactly(
                PricePathExport.HEADER.strip(),
                "7,2026-07-31T00:30:00Z,005930,PRICE_SPIKE,WARNING,UP,4,3,1.333,5,70000,71400,2,0.5,",
                "7,2026-07-31T00:30:00Z,005930,PRICE_SPIKE,WARNING,UP,4,3,1.333,5,70000,,,,");
    }

    @Test
    void readsSignalsFromTheStartOfTheFirstKstDay() {
        when(repository.findPathRows(any(), any())).thenReturn(List.of());

        export.csv(2);

        // 2026-07-30 00:00 KST
        verify(repository).findPathRows(Instant.parse("2026-07-29T15:00:00Z"), NOW);
    }

    @Test
    void neutralizesSpreadsheetFormulasAndQuotesSeparators() {
        assertThat(PricePathExport.text("005930")).isEqualTo("005930");
        assertThat(PricePathExport.text("=HYPERLINK(1)")).isEqualTo("'=HYPERLINK(1)");
        assertThat(PricePathExport.text("a,b\"c")).isEqualTo("\"a,b\"\"c\"");
    }

    private static PricePathRow row(BigDecimal base, BigDecimal price, BigDecimal market) {
        return new PricePathRow(7L, Instant.parse("2026-07-31T00:30:00Z"), "005930", RuleType.PRICE_SPIKE,
                Severity.WARNING, Direction.UP, new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("1.333"),
                5, base, price, market, null);
    }
}
