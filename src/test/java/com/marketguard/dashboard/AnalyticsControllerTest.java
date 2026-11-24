package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.marketguard.domain.casework.SurveillanceCaseRepository;
import java.io.Writer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalyticsControllerTest {

    private final PricePathExport export = mock(PricePathExport.class);
    private final AnalyticsController controller = new AnalyticsController(
            mock(SurveillanceCaseRepository.class), mock(SignalAnalyticsService.class), export);

    @Test
    void streamsPricePathsAsADownloadableCsvFile() throws Exception {
        doAnswer(call -> {
            call.<Writer>getArgument(1).write(PricePathExport.HEADER);
            return null;
        }).when(export).write(eq(31), any());
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.pricePaths(31, response);

        assertThat(response.getContentAsString()).isEqualTo(PricePathExport.HEADER);
        assertThat(response.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"marketguard-price-paths.csv\"");
    }
}
