// 개요 화면: 상태별 사건 수, 처리 대기 사건, 최근 신호, 거래대금 상위.
import { api } from "./api.js";
import { OPEN_STATUSES, chgHtml, escapeHtml, fmtCompact, fmtNumber, fmtTime, ruleLabel, severityDot, stockLabel } from "./format.js";
import { errorLine, loadingLine, stateLine } from "./ui.js";
import { caseTableRow } from "./cases.js";

const $ = id => document.getElementById(id);
const FEED_SIZE = 10;
let recentSignals = [];
let todayCount = { since: null, signals: 0, critical: 0 };

/** 오늘(KST) 0시의 ISO 시각. 서버가 이 시각 이후 신호를 전부 센다. */
export function startOfTodayKst(now = Date.now()) {
    const kstDate = new Date(now + 9 * 3600 * 1000).toISOString().slice(0, 10);
    return new Date(`${kstDate}T00:00:00+09:00`).toISOString();
}

export async function refreshOverview() {
    await Promise.allSettled([loadCounts(), loadQueue(), loadSignals(), loadMovers()]);
}

async function countCases(status) {
    const page = await api.cases({ status, size: 1, page: 0 });
    return Number(page.totalElements || 0);
}

async function loadCounts() {
    const targets = { kpiNew: "NEW", kpiReviewing: "REVIEWING", kpiEscalated: "ESCALATED" };
    await Promise.all(Object.entries(targets).map(async ([id, status]) => {
        try {
            const count = await countCases(status);
            $(id).textContent = fmtNumber(count);
            if (status === "NEW") updateNavCount(count);
        } catch {
            $(id).textContent = "–";
        }
    }));
}

export async function refreshNewCaseBadge() {
    try { updateNavCount(await countCases("NEW")); } catch { /* 다음 갱신 때 다시 시도 */ }
}

function updateNavCount(count) {
    const badge = $("navNewCount");
    badge.hidden = count === 0;
    badge.textContent = count > 99 ? "99+" : String(count);
    badge.setAttribute("aria-label", `신규 ${count}건`);
}

function tableMessage(html) {
    return `<tr><td colspan="5">${html}</td></tr>`;
}

async function loadQueue() {
    const body = $("priorityCases");
    if (!body.children.length) body.innerHTML = tableMessage(loadingLine());
    try {
        const page = await api.cases({ status: OPEN_STATUSES.join(","), size: 8, page: 0, sort: "score", direction: "desc" });
        const cases = page.content || [];
        body.innerHTML = cases.length ? cases.map(caseTableRow).join("") : tableMessage(stateLine("처리할 사건이 없습니다."));
    } catch (error) {
        body.innerHTML = tableMessage(errorLine("사건을 불러오지 못했습니다.", error));
    }
}

async function loadSignals() {
    try {
        const since = startOfTodayKst();
        const [feed, count] = await Promise.all([api.anomalies(FEED_SIZE), api.signalCount(since)]);
        recentSignals = feed || [];
        todayCount = count;
        renderSignals();
    } catch (error) {
        $("signalFeed").innerHTML = `<li style="display:block;border:0">${errorLine("신호를 불러오지 못했습니다.", error)}</li>`;
    }
}

function renderSignals(freshCount = 0) {
    const { signals, critical } = todayCount;
    $("kpiToday").innerHTML = fmtNumber(signals) + (critical ? `<small>심각 ${fmtNumber(critical)}</small>` : "");
    const feed = $("signalFeed");
    if (recentSignals.length === 0) {
        feed.innerHTML = `<li style="display:block;border:0">${stateLine("아직 탐지된 신호가 없습니다.")}</li>`;
        return;
    }
    feed.innerHTML = recentSignals.slice(0, FEED_SIZE).map((signal, index) =>
        `<li${index < freshCount ? ` class="fresh"` : ""}><time>${escapeHtml(fmtTime(signal.detectedAt))}</time>` +
        `<div class="what"><div>${severityDot(signal.severity)} <span class="sm">${escapeHtml(ruleLabel(signal.ruleType))}</span> · ${stockLabel(signal.stockName, signal.stockCode)}</div>` +
        `<p>${escapeHtml(signal.summary || signal.message)}</p></div></li>`
    ).join("");
}

/**
 * 실시간 알림 한 건을 피드 맨 앞에 붙이고, 오늘 신호면 개수도 올린다.
 * 자정(KST)이 지났으면 어제 개수에 더하지 않고 오늘 개수를 다시 불러온다.
 */
export function pushSignal(notification) {
    recentSignals.unshift({ ...notification, summary: notification.message });
    recentSignals = recentSignals.slice(0, FEED_SIZE);
    if (todayCount.since !== startOfTodayKst()) {
        renderSignals(1);
        loadSignals();
        return;
    }
    if (Date.parse(notification.detectedAt) >= Date.parse(todayCount.since)) {
        todayCount = {
            ...todayCount,
            signals: todayCount.signals + 1,
            critical: todayCount.critical + (notification.severity === "CRITICAL" ? 1 : 0)
        };
    }
    renderSignals(1);
}

async function loadMovers() {
    const host = $("topMovers");
    try {
        const rows = (await api.rankings("MARKET_TRADING_AMOUNT", 5)) || [];
        host.innerHTML = rows.length
            ? `<div class="table-wrap"><table class="t"><thead><tr><th class="rank">#</th><th>종목</th>` +
              `<th class="r">현재가</th><th class="r">등락률</th><th class="r">거래대금</th></tr></thead><tbody>` +
              rows.map(it => `<tr><td class="rank">${Number(it.rank)}</td><td>${stockLabel(it.name, it.code)}</td>` +
                  `<td class="r">${fmtNumber(it.price)}</td><td class="r">${chgHtml(it.changePercent)}</td>` +
                  `<td class="r">${fmtCompact(it.tradingAmount, "원")}</td></tr>`).join("") +
              `</tbody></table></div>`
            : stateLine("시세 데이터가 없습니다.", { hint: "토스 API 키 설정과 장 운영 시간을 확인하세요." });
    } catch (error) {
        host.innerHTML = errorLine("랭킹을 불러오지 못했습니다.", error);
    }
}
