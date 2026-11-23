// 통계 화면: 규칙별 검토 결과와 감사 로그.
import { api } from "./api.js";
import { escapeHtml, fmtDateTime, fmtDuration, fmtNumber, ruleLabel } from "./format.js";
import { errorLine, loadingLine, stateLine } from "./ui.js";

const $ = id => document.getElementById(id);

const ACTION_LABELS = {
    CASE_STATUS_CHANGED: "사건 상태 변경",
    CASE_NOTE_CREATED: "메모 작성",
    TOSS_TOKEN_ISSUE: "토스 토큰 발급"
};
const OUTCOME_LABELS = { SUCCESS: "성공", FAILURE: "실패" };

export async function refreshInsights() {
    await Promise.allSettled([loadRules(), loadAudit()]);
}

const fullRow = (columns, html) => `<tr><td colspan="${columns}">${html}</td></tr>`;
const percent = value => `${(Number(value) || 0).toFixed(1)}%`;

async function loadRules() {
    const body = $("ruleAnalytics");
    if (!body.children.length) body.innerHTML = fullRow(6, loadingLine());
    try {
        const rows = (await api.ruleAnalytics()) || [];
        body.innerHTML = rows.length ? rows.map(item =>
            `<tr><td>${escapeHtml(ruleLabel(item.ruleType))}</td>` +
            `<td class="r">${fmtNumber(item.occurrenceCount)}</td>` +
            `<td class="r">${fmtNumber(item.caseCount)}</td>` +
            `<td class="r">${percent(item.dismissedPercent)}</td>` +
            `<td class="r">${percent(item.escalatedPercent)}</td>` +
            `<td class="r">${escapeHtml(fmtDuration(item.averageReviewSeconds))}</td></tr>`
        ).join("") : fullRow(6, stateLine("검토된 사건이 아직 없습니다."));
    } catch (error) {
        body.innerHTML = fullRow(6, errorLine("검토 결과를 불러오지 못했습니다.", error));
    }
}

async function loadAudit() {
    const body = $("auditRows");
    if (!body.children.length) body.innerHTML = fullRow(5, loadingLine());
    try {
        const rows = (await api.audit(100)) || [];
        body.innerHTML = rows.length ? rows.map(x =>
            `<tr><td class="nowrap num">${escapeHtml(fmtDateTime(x.createdAt))}</td>` +
            `<td class="nowrap" title="${escapeHtml(x.action)}">${escapeHtml(ACTION_LABELS[x.action] || x.action)}</td>` +
            `<td><span class="dot ${x.outcome === "SUCCESS" ? "ok" : "fail"}">${escapeHtml(OUTCOME_LABELS[x.outcome] || x.outcome)}</span></td>` +
            `<td class="r">${fmtNumber(Number(x.latencyMs) || 0)}ms</td>` +
            `<td class="muted" style="overflow-wrap:anywhere">${escapeHtml(x.detail)}</td></tr>`
        ).join("") : fullRow(5, stateLine("감사 기록이 없습니다."));
    } catch (error) {
        body.innerHTML = fullRow(5, errorLine("감사 로그를 불러오지 못했습니다.", error));
    }
}
