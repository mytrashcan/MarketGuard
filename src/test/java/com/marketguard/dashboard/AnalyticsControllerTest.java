package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketguard.domain.casework.SurveillanceCaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class AnalyticsControllerTest {

    private final PricePathExport export = mock(PricePathExport.class);
    private final AnalyticsController controller = new AnalyticsController(
            mock(SurveillanceCaseRepository.class), mock(SignalAnalyticsService.class), export);

    @Test
    void servesPricePathsAsADownloadableCsvFile() {
        when(export.csv(31)).thenReturn(PricePathExport.HEADER);

        ResponseEntity<String> response = controller.pricePaths(31);

        assertThat(response.getBody()).isEqualTo(PricePathExport.HEADER);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"marketguard-price-paths.csv\"");
    }
}
