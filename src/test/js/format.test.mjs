// 대시보드 표시 로직 단위 테스트. 실행: node --test src/test/js/*.test.mjs
// Node 20.19+ 또는 22.12+ 필요: format.js를 package.json 없이 ES 모듈로 읽으려면 모듈 자동 판별이 있어야 한다.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
    chgHtml, escapeHtml, fmtCompact, fmtDuration, fmtMetric, fmtRelative, ruleLabel,
    scoreHtml, statusDot, stockLabel, toBarTime
} from "../../main/resources/static/js/format.js";

test("escapeHtml escapes every HTML-significant character", () => {
    assert.equal(escapeHtml(`<a href="x" onclick='y'>&</a>`),
        "&lt;a href=&quot;x&quot; onclick=&#39;y&#39;&gt;&amp;&lt;/a&gt;");
    assert.equal(escapeHtml(null), "");
    assert.equal(escapeHtml(0), "0");
});

test("labels escape values they did not create", () => {
    assert.equal(stockLabel("<b>삼성</b>", "005930"),
        `<span class="stock">&lt;b&gt;삼성&lt;/b&gt;</span><span class="code">005930</span>`);
    assert.match(statusDot(`"><script>`), /^<span class="dot &quot;&gt;&lt;script&gt;">/);
    assert.equal(ruleLabel("UNKNOWN_RULE"), "UNKNOWN_RULE");
    assert.equal(ruleLabel(undefined), "알 수 없는 규칙");
});

test("chgHtml shows direction with color class, arrow and sign", () => {
    assert.equal(chgHtml(1.234), `<span class="up">▲ +1.23%</span>`);
    assert.equal(chgHtml(-0.5), `<span class="down">▼ −0.50%</span>`);
    assert.equal(chgHtml(0), `<span class="flat"> 0.00%</span>`);
    assert.equal(chgHtml(null), `<span class="flat">–</span>`);
    assert.equal(chgHtml("2"), `<span class="up">▲ +2.00%</span>`);
});

test("fmtCompact uses Korean large-number units and hides empty values", () => {
    assert.equal(fmtCompact(1.25e12, "원"), "1.3조원");
    assert.equal(fmtCompact(350_000_000, "원"), "3.5억원");
    assert.equal(fmtCompact(12_345, "주"), "1만주");
    assert.equal(fmtCompact(999), "999");
    assert.equal(fmtCompact(0), "–");
    assert.equal(fmtCompact(null), "–");
});

test("fmtMetric keeps two decimals and marks missing values", () => {
    assert.equal(fmtMetric("3.456", "%"), "3.46 %");
    assert.equal(fmtMetric(null, "%"), "해당 없음");
    assert.equal(fmtMetric("abc"), "abc");
});

test("scoreHtml clamps the score to 0..100", () => {
    assert.match(scoreHtml(140, "HIGH"), /width:100%.*100<\/span>$/);
    assert.match(scoreHtml(-5, "LOW"), /width:0%.*0<\/span>$/);
});

test("toBarTime renders chart times on the KST wall clock", () => {
    // 2026-07-30 16:00 UTC는 KST 2026-07-31 01:00 → 일봉은 KST 날짜
    assert.equal(toBarTime("2026-07-30T16:00:00Z", "1d"), "2026-07-31");
    // 분봉은 UTC 초 + 9시간
    assert.equal(toBarTime("2026-07-31T00:00:00Z", "1m"), Date.parse("2026-07-31T00:00:00Z") / 1000 + 9 * 3600);
});

test("fmtRelative and fmtDuration use short Korean units", () => {
    const now = Date.parse("2026-07-31T01:00:00Z");
    assert.equal(fmtRelative("2026-07-31T00:59:30Z", now), "방금");
    assert.equal(fmtRelative("2026-07-31T00:55:00Z", now), "5분 전");
    assert.equal(fmtRelative("2026-07-30T22:00:00Z", now), "3시간 전");
    assert.equal(fmtRelative("not-a-date", now), "-");
    assert.equal(fmtDuration(45), "45초");
    assert.equal(fmtDuration(600), "10분");
    assert.equal(fmtDuration(5400), "1.5시간");
    assert.equal(fmtDuration(null), "–");
});
