package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketguard.collector.BoardDataService;
import com.marketguard.collector.client.TossMarketDataClient;
import com.marketguard.domain.anomaly.AnomalyRepository;
import com.marketguard.domain.audit.AuditLogRepository;
import com.marketguard.domain.marketdata.PriceSnapshotRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardControllerCharacterizationTest {

    @Test
    void documentsThatMalformedCandleParametersCurrentlyReachTheUpstreamClient() {
        TossMarketDataClient client = mock(TossMarketDataClient.class);
        when(client.fetchCandles("not-a-symbol", "unsupported", 10_000)).thenReturn(List.of());
        DashboardController controller = new DashboardController(
                mock(AnomalyRepository.class),
                mock(PriceSnapshotRepository.class),
                client,
                mock(BoardDataService.class),
                mock(AuditLogRepository.class));

        assertThat(controller.candles("not-a-symbol", "unsupported", 10_000)).isEmpty();
        verify(client).fetchCandles("not-a-symbol", "unsupported", 10_000);
    }
}
