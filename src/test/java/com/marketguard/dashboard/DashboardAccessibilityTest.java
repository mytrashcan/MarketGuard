package com.marketguard.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DashboardAccessibilityTest {

    @Test
    void announcesThroughOneStatusRegionInsteadOfLargeLiveRegions() {
        String html = DashboardSources.indexHtml();

        // 다시 그릴 때마다 내용 전체를 읽히지 않도록 큰 영역은 live region이 아니어야 한다.
        assertThat(html).doesNotContainPattern(Pattern.compile("id=\"caseList\"[^>]*aria-live"));
        assertThat(html).doesNotContainPattern(Pattern.compile("id=\"caseDetail\"[^>]*aria-live"));
        assertThat(html).containsPattern(Pattern.compile("id=\"srStatus\"[^>]*role=\"status\""));
        assertThat(DashboardSources.script("ui.js")).contains("export function announce(text)");
    }

    @Test
    void movesFocusOnlyWhenTheUserOpensACase() {
        String cases = DashboardSources.script("cases.js");
        String main = DashboardSources.script("main.js");

        assertThat(cases).contains("<h2 id=\"caseHeading\" tabindex=\"-1\">",
                "if (userOpened) state.focusCaseIdPending = id;",
                "if (state.focusCaseIdPending === detail.id) {");
        assertThat(main).contains("openCase(caseId, { userOpened: true })");
        // 새로고침·실시간 갱신은 포커스를 옮기지 않는다.
        assertThat(main).contains("openCase(selectedCaseId())");
    }

    @Test
    void announcesFailuresOfUserRequests() {
        String cases = DashboardSources.script("cases.js");

        assertThat(cases).contains("announce(`사건 목록을 불러오지 못했습니다.",
                "announce(`사건 ${Number(id)}을 불러오지 못했습니다.");
    }
}
