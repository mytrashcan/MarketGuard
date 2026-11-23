// 시장 화면: 토스 시장 랭킹 표 + 선택한 종목의 시세와 차트.
import { api } from "./api.js";
import { chgHtml, escapeHtml, fmtCompact, fmtNumber, fmtTime, fmtWon, stockLabel } from "./format.js";
import { errorLine, loadingLine, stateLine } from "./ui.js";
import { chartsAvailable, createCandleChart } from "./charts.js";

const state = {
    rankingType: "MARKET_TRADING_AMOUNT",
    rows: [],
    query: "",
    selectedCode: null,
    interval: "1d",
    request: 0,
    chart: null,
    chartCode: null
};

const $ = id => document.getElementById(id);

export function initMarket() {
    document.querySelectorAll("[data-ranking]").forEach(button => button.addEventListener("click", () => {
        state.rankingType = button.dataset.ranking;
        document.querySelectorAll("[data-ranking]").forEach(item =>
            item.setAttribute("aria-selected", String(item === button)));
        state.selectedCode = null;
        loadRanking();
    }));
    $("rankingSearch").addEventListener("input", event => {
        state.query = event.target.value.trim().toLowerCase();
        renderRows();
    });
    const body = $("rankingBody");
    body.addEventListener("click", event => {
        const row = event.target.closest("tr[data-code]");
        if (row) select(row.dataset.code);
    });
    body.addEventListener("keydown", event => {
        const row = event.target.closest("tr[data-code]");
        if (!row) return;
        if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            select(row.dataset.code);
        } else if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            (event.key === "ArrowDown" ? row.nextElementSibling : row.previousElementSibling)?.focus();
        }
    });
    document.querySelectorAll("[data-interval]").forEach(button => button.addEventListener("click", () => {
        state.interval = button.dataset.interval;
        document.querySelectorAll("[data-interval]").forEach(item =>
            item.setAttribute("aria-pressed", String(item === button)));
        // 일봉은 날짜 축, 분봉은 시각 축이라 차트를 새로 만든다.
        state.chart?.remove();
        state.chart = null;
        state.chartCode = null;
        loadChart(state.selectedCode);
    }));
}

export const refreshMarket = () => loadRanking();
export const selectedStock = () => state.selectedCode;

function showState(html) {
    $("rankingState").innerHTML = html;
    $("rankingState").hidden = !html;
    $("rankingTable").hidden = Boolean(html);
}

async function loadRanking() {
    const request = ++state.request;
    if (state.rows.length === 0) showState(loadingLine());
    try {
        const rows = await api.rankings(state.rankingType, 50);
        if (request !== state.request) return;
        state.rows = rows || [];
        renderRows();
        const rankedAt = state.rows[0]?.rankedAt;
        $("rankingMeta").textContent = rankedAt ? `${fmtTime(rankedAt)} 집계 · 15초마다 갱신` : "";
        if (!state.selectedCode && state.rows.length) select(state.rows[0].code);
        else if (state.selectedCode) renderQuote();
    } catch (error) {
        if (request !== state.request) return;
        if (state.rows.length === 0) showState(errorLine("랭킹을 불러오지 못했습니다.", error));
    }
}

function visibleRows() {
    if (!state.query) return state.rows;
    return state.rows.filter(row =>
        row.code.toLowerCase().includes(state.query) || (row.name || "").toLowerCase().includes(state.query));
}

function renderRows() {
    const rows = visibleRows();
    if (state.rows.length === 0) {
        $("rankingBody").innerHTML = "";
        return showState(stateLine("랭킹 데이터가 없습니다.",
            { hint: "<code>TOSS_CLIENT_ID</code>와 <code>TOSS_CLIENT_SECRET</code> 설정, 장 운영 시간을 확인하세요." }));
    }
    if (rows.length === 0) {
        $("rankingBody").innerHTML = "";
        return showState(stateLine(`'${state.query}'와 일치하는 종목이 없습니다.`));
    }
    showState("");
    $("rankingBody").innerHTML = rows.map(it =>
        `<tr tabindex="0" data-code="${escapeHtml(it.code)}" aria-selected="${it.code === state.selectedCode}">` +
        `<td class="rank">${Number(it.rank) || "–"}</td>` +
        `<td class="nowrap">${stockLabel(it.name, it.code)}</td>` +
        `<td class="r">${fmtNumber(it.price)}</td>` +
        `<td class="r">${chgHtml(it.changePercent)}</td>` +
        `<td class="r">${fmtCompact(it.volume, "주")}</td>` +
        `<td class="r">${fmtCompact(it.tradingAmount, "원")}</td></tr>`
    ).join("");
}

function select(code) {
    state.selectedCode = code;
    document.querySelectorAll("#rankingBody tr[data-code]").forEach(row =>
        row.setAttribute("aria-selected", String(row.dataset.code === code)));
    renderQuote();
    loadChart(code);
}

function renderQuote() {
    const it = state.rows.find(row => row.code === state.selectedCode);
    if (!it) return;
    $("quoteEmpty").hidden = true;
    $("quoteCard").hidden = false;
    $("quoteName").textContent = it.name || it.code;
    $("quoteCode").textContent = it.code;
    $("quotePrice").textContent = fmtWon(it.price);
    $("quoteChange").innerHTML = chgHtml(it.changePercent);
    $("quoteBase").textContent = fmtWon(it.previousClose);
    $("quoteVolume").textContent = fmtCompact(it.volume, "주");
    $("quoteAmount").textContent = fmtCompact(it.tradingAmount, "원");
    $("quoteTime").textContent = fmtTime(it.rankedAt);
    $("quoteCasesLink").dataset.stock = it.code;
}

async function loadChart(code) {
    if (!code || state.chartCode === code) return;
    state.chartCode = code;
    const note = $("quoteChartNote");
    note.hidden = true;
    if (!chartsAvailable()) {
        note.textContent = "차트 라이브러리를 불러오지 못했습니다.";
        note.hidden = false;
        return;
    }
    if (!state.chart) state.chart = createCandleChart($("quoteChart"), { timeVisible: state.interval === "1m" });
    try {
        const candles = await api.candles(code, state.interval, 120);
        if (state.chartCode !== code) return;
        if (state.chart.setCandles(candles, state.interval).length === 0) {
            note.textContent = state.interval === "1m" ? "장중에만 1분봉이 있습니다." : "일봉 데이터가 없습니다.";
            note.hidden = false;
        }
    } catch (error) {
        note.textContent = `차트를 불러오지 못했습니다. ${error.message}`;
        note.hidden = false;
        state.chartCode = null;
    }
}

export function focusSearch() {
    $("rankingSearch").focus();
}
