package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DashboardStaticSecurityTest {

    @Test
    void escapesStoredTextBeforeRenderingIt() {
        String cases = DashboardSources.script("cases.js");
        String insights = DashboardSources.script("insights.js");

        assertThat(cases).contains("escapeHtml(signal.summary)", "escapeHtml(note.note)",
                "escapeHtml(signal.explanation)", "escapeHtml(item.summary)");
        assertThat(insights).contains("escapeHtml(x.detail)", "escapeHtml(x.action)");
        assertThat(DashboardSources.script("format.js")).contains("escapeHtml(shown)", "escapeHtml(code)");
        assertThat(DashboardSources.all()).doesNotContain(
                "${signal.summary}", "${note.note}", "${x.detail}", "${x.action}", "${item.summary}",
                "${detail.caution}", "${item.stockName}");
    }

    @Test
    void pinsExternalScriptsWithIntegrityAndHasNoInlineScripts() {
        String html = DashboardSources.indexHtml();

        assertThat(count(html, "integrity=\"sha384-")).isEqualTo(3);
        assertThat(count(html, "crossorigin=\"anonymous\"")).isEqualTo(3);
        Matcher scriptTags = Pattern.compile("<script([^>]*)>").matcher(html);
        while (scriptTags.find()) {
            assertThat(scriptTags.group(1)).as("every script must load from a src").contains("src=");
        }
        assertThat(html).doesNotContainPattern("\\son[a-z]+=\"");
    }

    @Test
    void asksForTheOperatorTokenInPageInsteadOfABlockingPrompt() {
        String all = DashboardSources.all();

        assertThat(all).contains("id=\"tokenDialog\"", "askOperatorToken", "X-Operator-Token");
        assertThat(all).doesNotContain("window.prompt(", "alert(");
    }

    @Test
    void rendersTheMarketBoardFromCachedTossRankings() {
        String all = DashboardSources.all();

        assertThat(all).contains(
                "data-ranking=\"MARKET_TRADING_AMOUNT\"",
                "data-ranking=\"MARKET_TRADING_VOLUME\"",
                "data-ranking=\"TOP_GAINERS\"",
                "data-ranking=\"TOP_LOSERS\"",
                "/api/rankings?type=",
                "it.tradingAmount");
        assertThat(all).doesNotContain("/api/prices/live", "/topic/prices");
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
