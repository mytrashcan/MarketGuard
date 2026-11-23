package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DashboardPriceChangeDisplayTest {

    @Test
    void showsChangeRateWithDirectionArrowAndWithoutDataSourceBadges() {
        String format = DashboardSources.script("format.js");

        assertThat(format).contains("export function chgHtml(pct)", "\"▲\"", "\"▼\"");
        assertThat(DashboardSources.all()).doesNotContain(
                "const sourceTag =",
                "토스 공식",
                "일봉 계산",
                "무수정 일봉",
                "it.changeSource");
    }
}
