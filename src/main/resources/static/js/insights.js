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

/** 표본이 이보다 적으면 신뢰구간이 너무 넓어 판단하지 않는다. */
const MIN_SAMPLES = 10;
let measureDays = 14;
let measureRequest = 0;

export function initInsights() {
    $("measureDays").addEventListener("click", event => {
        const button = event.target.closest("[data-days]");
        if (!button) return;
        measureDays = Number(button.dataset.days);
        document.querySelectorAll("#measureDays [data-days]").forEach(item =>
            item.setAttribute("aria-pressed", String(item === button)));
        loadMeasurement();
    });
}

export async function refreshInsights() {
    await Promise.allSettled([loadMeasurement(), loadRules(), loadAudit()]);
}

const enough = interval => interval != null && interval.samples >= MIN_SAMPLES;

/**
 * 95% 신뢰구간이 50%를 벗어날 때만 결론을 낸다. 업종 보정 → 시장 보정 → 보정 전 순서로
 * 표본이 충분한 값을 쓰고, 어떤 기준으로 판단했는지 밝힌다.
 */
function verdict(rule) {
    const [interval, suffix] = enough(rule.followedAfterSector) ? [rule.followedAfterSector, " (업종 대비)"]
        : enough(rule.followedAfterMarket) ? [rule.followedAfterMarket, ""]
        : [rule.followed, " (시장 보정 전)"];
    if (!enough(interval)) return `표본 부족 (${fmtNumber(interval?.samples || 0)}건)`;
    if (Number(interval.lowerPercent) > 50) return `신호 방향으로 이어짐${suffix}`;
    if (Number(interval.upperPercent) < 50) return `반대로 되돌아감${suffix}`;
    return `우연과 구분 안 됨${suffix}`;
}

const BAND_LABELS = { NEAR: "×1–1.5", MID: "×1.5–2", STRONG: "×2+" };

/** 임계 배수 구간별 시장 대비 지속률. 표본이 적은 구간은 건수만 보인다. */
function bandsCell(bands) {
    if (!bands || bands.length === 0) return `<span class="subtle">–</span>`;
    return bands.map(band => {
        const interval = band.followedAfterMarket || band.followed;
        const value = enough(interval) ? percent(interval.ratePercent) : `<span class="subtle">${fmtNumber(interval?.samples || 0)}건</span>`;
        return `<span class="nowrap">${escapeHtml(BAND_LABELS[band.band] || band.band)} ${value}</span>`;
    }).join(" · ");
}

/** "52.9% 45–61"처럼 비율과 95% 신뢰구간. */
function rateCell(interval) {
    if (interval == null) return "–";
    return `${percent(interval.ratePercent)} <span class="subtle xs">${Math.round(interval.lowerPercent)}–${Math.round(interval.upperPercent)}</span>`;
}

const MONTH_DAY = new Intl.DateTimeFormat("ko-KR", { month: "numeric", day: "numeric", timeZone: "UTC" });
const dayLabel = iso => MONTH_DAY.format(new Date(`${iso}T00:00:00Z`));

function renderVolume(daily) {
    const host = $("dailyVolume");
    const max = Math.max(1, ...daily.map(day => day.signals));
    // 날짜 라벨은 약 56px마다 하나씩만 둔다(좁은 화면에서 겹치지 않게).
    const step = Math.max(1, Math.ceil(daily.length / Math.max(2, Math.floor(host.clientWidth / 56))));
    host.innerHTML = daily.map((day, index) => {
        const total = (day.signals / max) * 100;
        const critical = day.signals ? (day.critical / day.signals) * total : 0;
        const label = `${dayLabel(day.date)}: 신호 ${day.signals}건, 심각 ${day.critical}건, 종목 ${day.stocks}개`;
        return `<div class="bar" title="${escapeHtml(label)}">` +
            `<i style="height:${(total - critical).toFixed(1)}%"></i><i class="crit" style="height:${critical.toFixed(1)}%"></i>` +
            ((daily.length - 1 - index) % step === 0 ? `<span>${escapeHtml(dayLabel(day.date))}</span>` : "") +
            `</div>`;
    }).join("");
    const total = daily.reduce((sum, day) => sum + day.signals, 0);
    host.setAttribute("aria-label", `최근 ${daily.length}일 신호 ${total}건, 하루 최대 ${max}건`);
}

async function loadMeasurement() {
    const body = $("ruleOutcomes");
    if (!body.children.length) body.innerHTML = fullRow(9, loadingLine());
    // 기간을 빠르게 바꾸면 늦게 도착한 이전 응답이 화면을 덮지 않도록 마지막 요청만 반영한다.
    const request = ++measureRequest;
    try {
        const view = await api.signalAnalytics(measureDays);
        if (request !== measureRequest) return;
        const coverage = view.coverage;
        const unmeasured = coverage.noPriceData + coverage.notApplicable;
        $("mTotal").textContent = fmtNumber(view.totalSignals);
        $("mPerDay").textContent = (view.totalSignals / view.days).toLocaleString("ko-KR", { maximumFractionDigits: 1 });
        $("mMeasured").innerHTML = fmtNumber(coverage.measured) +
            (coverage.pending ? `<small style="color:var(--fg-subtle)">대기 ${fmtNumber(coverage.pending)}</small>` : "");
        $("mUnmeasured").textContent = fmtNumber(unmeasured);
        $("medianHead").textContent = `${view.horizonMinutes}분 변동 중앙값`;
        renderVolume(view.daily);
        body.innerHTML = view.rules.length ? view.rules.map(rule =>
            `<tr><td>${escapeHtml(ruleLabel(rule.ruleType))}</td>` +
            `<td class="r">${fmtNumber(rule.signals)}</td>` +
            `<td class="r">${fmtNumber(rule.measured)}</td>` +
            `<td class="r nowrap">${rateCell(rule.followed)}</td>` +
            `<td class="r nowrap">${rateCell(rule.followedAfterMarket)}</td>` +
            `<td class="r nowrap hide-sm">${rateCell(rule.followedAfterSector)}</td>` +
            `<td class="r">${rule.medianAbsChangePercent == null ? "–" : `${Number(rule.medianAbsChangePercent).toFixed(2)}%`}</td>` +
            `<td class="sm">${bandsCell(rule.bands)}</td>` +
            `<td class="verdict">${escapeHtml(verdict(rule))}</td></tr>`
        ).join("") : fullRow(9, stateLine("이 기간에 탐지된 신호가 없습니다."));
        $("measureNote").textContent =
            `방향 지속률은 신호 ${view.horizonMinutes}분 뒤 가격이 신호 방향(상승·매수 우위는 위, 하락·매도 우위는 아래)으로 움직인 비율입니다. ` +
            `50%면 우연과 같습니다. 시장 대비는 같은 ${view.horizonMinutes}분 동안 스캔 종목 변동률의 중앙값을 뺀 뒤의 지속률로, ` +
            `시장 전체 흐름에 올라탄 신호를 걸러냅니다(지수가 아닌 동일 가중 기준). ` +
            (view.rules.some(rule => rule.followedAfterSector)
                ? `업종 대비는 같은 업종 다른 종목의 변동률 중앙값을 뺀 값입니다. `
                : `업종 대비는 업종 파일(outcome.sectors-file)을 설정하면 계산됩니다. `) +
            `강도별은 임계값을 몇 배 넘은 신호인지에 따라 나눈 시장 대비 지속률로, 강할수록 높아지면 임계값을 올릴 근거가 됩니다. ` +
            `작은 숫자는 95% 신뢰구간이며, 구간 전체가 50%보다 위나 아래일 때만 결론을 냅니다(표본 ${MIN_SAMPLES}건 미만은 판단하지 않음). ` +
            `장 마감 직전 신호와 시장 전체 신호는 측정하지 못합니다.` +
            (coverage.otherHorizon
                ? ` 측정 기준(horizon)이 바뀌기 전에 잰 결과 ${fmtNumber(coverage.otherHorizon)}건은 섞지 않고 제외했습니다.`
                : "");
    } catch (error) {
        if (request !== measureRequest) return;
        body.innerHTML = fullRow(9, errorLine("신호 측정 결과를 불러오지 못했습니다.", error));
    }
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
