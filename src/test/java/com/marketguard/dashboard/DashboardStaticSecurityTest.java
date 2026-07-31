package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardStaticSecurityTest {

    @Test
    void escapesStoredTextAndPinsExternalScriptsWithIntegrity() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));

        assertThat(html).contains("escapeHtml(signal.summary)", "escapeHtml(note.note)",
                "escapeHtml(x.detail)", "escapeHtml(x.action)");
        assertThat(html).contains("escapeHtml(item.stockName");
        assertThat(html).doesNotContain("${signal.summary}", "${note.note}", "${x.detail}", "${x.action}");
        assertThat(count(html, "integrity=\"sha384-")).isEqualTo(3);
        assertThat(count(html, "crossorigin=\"anonymous\"")).isEqualTo(3);
    }

    @Test
    void rendersTheMainBoardFromCachedTossRankings() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"));

        assertThat(html).contains(
                "data-ranking=\"MARKET_TRADING_AMOUNT\"",
                "data-ranking=\"MARKET_TRADING_VOLUME\"",
                "data-ranking=\"TOP_GAINERS\"",
                "data-ranking=\"TOP_LOSERS\"",
                "/api/rankings?type=",
                "it.tradingAmount");
        assertThat(html).doesNotContain(
                "fetch(\"/api/prices/live\")",
                "stomp.subscribe(\"/topic/prices\"");
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
