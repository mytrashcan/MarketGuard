package com.marketguard.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketguard.collector.client.TossMarketDataClient;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MarketSessionTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @Test
    void opensAtTheOfficialStartAndClosesAtTheOfficialEnd() {
        MarketDay tradingDay = new MarketDay(DATE, true, LocalTime.of(9, 0), LocalTime.of(15, 30));

        assertThat(sessionAt("2026-07-15T00:00:00Z", tradingDay).isKrxOpen()).isTrue();
        assertThat(sessionAt("2026-07-15T06:29:59Z", tradingDay).isKrxOpen()).isTrue();
        assertThat(sessionAt("2026-07-15T06:30:00Z", tradingDay).isKrxOpen()).isFalse();
    }

    @Test
    void staysClosedOnAnOfficialHoliday() {
        assertThat(sessionAt("2026-07-15T03:00:00Z",
                new MarketDay(DATE, false, null, null)).isKrxOpen()).isFalse();
    }

    @Test
    void failsClosedWhenCalendarLookupFails() {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        when(client.fetchKrMarketToday()).thenThrow(new IllegalStateException("upstream unavailable"));
        Clock clock = Clock.fixed(Instant.parse("2026-07-15T03:00:00Z"), ZoneOffset.UTC);

        assertThat(new MarketSession(client, clock).isKrxOpen()).isFalse();
    }

    private MarketSession sessionAt(String instant, MarketDay marketDay) {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        when(client.fetchKrMarketToday()).thenReturn(Optional.of(marketDay));
        return new MarketSession(client, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
}
