package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardPriceChangeDisplayTest {

    @Test
    void omitsDataSourceBadgesFromTheDisplayedChangeRate() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));

        assertThat(html).contains("function chgHtml(pct, closed)");
        assertThat(html).doesNotContain(
                "const sourceTag =",
                "<span class=\"ctag\">토스 공식</span>",
                "<span class=\"ctag\">일봉 계산</span>",
                "chgHtml(it.changePercent, it.closed, it.changeSource)");
    }
}
