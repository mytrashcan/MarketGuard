// 진입점: 해시 라우팅, 주기적 갱신, 단축키, 실시간 알림(WebSocket).
import { fmtTime, ruleLabel } from "./format.js";
import { initTheme, toast } from "./ui.js";
import { renderHelp } from "./help.js";
import { focusSearch, initMarket, refreshMarket } from "./market.js";
import { closeCaseDetail, initCases, loadCases, openCase, selectedCaseId, setFilters, stepCase } from "./cases.js";
import { pushSignal, refreshNewCaseBadge, refreshOverview } from "./overview.js";
import { refreshInsights } from "./insights.js";

const VIEWS = {
    overview: { title: "개요", refresh: refreshOverview, poll: 30000 },
    market: { title: "시장", refresh: refreshMarket, poll: 15000 },
    cases: { title: "사건", refresh: loadCases, poll: 60000 },
    insights: { title: "통계", refresh: refreshInsights, poll: 0 },
    help: { title: "도움말", refresh: renderHelp, poll: 0 }
};
const GO_KEYS = { o: "overview", m: "market", c: "cases", s: "insights", h: "help" };

const $ = id => document.getElementById(id);
let currentView = null;
let pollTimer = null;

function navigate(hash) {
    if (location.hash === `#${hash}`) route();
    else location.hash = hash;
}

/** "#case-12"는 사건 상세, 나머지는 화면 이름. 모르는 값이면 개요. */
function parseHash() {
    const raw = location.hash.replace(/^#/, "");
    const caseMatch = raw.match(/^case-(\d+)$/);
    if (caseMatch) return { view: "cases", caseId: Number(caseMatch[1]) };
    return { view: VIEWS[raw] ? raw : "overview", caseId: null };
}

async function route() {
    const { view, caseId } = parseHash();
    const changed = view !== currentView;
    currentView = view;
    document.querySelectorAll("[data-view-panel]").forEach(panel => { panel.hidden = panel.dataset.viewPanel !== view; });
    document.querySelectorAll(".nav a[data-view]").forEach(link => {
        if (link.dataset.view === view) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
    });
    $("viewTitle").textContent = VIEWS[view].title;
    document.title = `${VIEWS[view].title} · MarketGuard`;
    $("refreshView").hidden = !VIEWS[view].refresh;

    if (view === "cases") {
        if (caseId) openCase(caseId); else closeCaseDetail();
    }
    if (changed) {
        await refreshCurrent();
        schedulePoll();
    }
}

async function refreshCurrent() {
    const refresh = VIEWS[currentView]?.refresh;
    if (!refresh) return;
    await refresh();
    $("lastUpdated").textContent = `${fmtTime(new Date().toISOString())} 기준`;
}

function schedulePoll() {
    clearInterval(pollTimer);
    const interval = VIEWS[currentView]?.poll;
    if (interval) pollTimer = setInterval(() => { if (!document.hidden) refreshCurrent(); }, interval);
}

function renderRuleHelp() {
    renderHelp();
}

/* ---------------- 단축키 ---------------- */

let pendingGo = false;
let goTimer = null;

function isTyping(target) {
    return target instanceof HTMLElement
        && (target.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(target.tagName));
}

function onKeydown(event) {
    if (event.metaKey || event.ctrlKey || event.altKey || $("tokenDialog").open) return;
    if (event.key === "Escape" && isTyping(event.target)) { event.target.blur(); return; }
    if (isTyping(event.target)) return;
    const key = event.key.toLowerCase();

    if (pendingGo) {
        pendingGo = false;
        clearTimeout(goTimer);
        if (GO_KEYS[key]) { event.preventDefault(); navigate(GO_KEYS[key]); }
        return;
    }
    if (key === "g") { pendingGo = true; goTimer = setTimeout(() => { pendingGo = false; }, 1200); return; }
    if (key === "r") { event.preventDefault(); refreshCurrent(); return; }
    if (key === "/") {
        event.preventDefault();
        if (currentView === "cases") $("filterStock").focus();
        else { navigate("market"); focusSearch(); }
        return;
    }
    if (currentView === "cases") {
        if (key === "j") { event.preventDefault(); stepCase(1); }
        else if (key === "k") { event.preventDefault(); stepCase(-1); }
        else if (key === "escape" && selectedCaseId()) navigate("cases");
    }
}

/* ---------------- 실시간 알림 ---------------- */

function setConnected(on, text) {
    const el = $("connStatus");
    el.className = `dot ${on ? "live" : "off"}`;
    el.textContent = text || (on ? "실시간 연결됨" : "재연결 중");
}

function onAnomaly(notification) {
    pushSignal(notification);
    refreshNewCaseBadge();
    const stockCode = /^\d{6}$/.test(notification.stockCode) ? notification.stockCode : "";
    toast(`${ruleLabel(notification.ruleType)} · ${notification.stockName || notification.stockCode}`, {
        body: notification.message,
        action: { label: "보기", onClick: () => { setFilters({ status: "OPEN", stockCode }); navigate("cases"); } }
    });
    if (currentView === "overview") refreshOverview();
    if (currentView === "cases") {
        loadCases();
        if (selectedCaseId()) openCase(selectedCaseId());
    }
}

let reconnectDelay = 2000;
function connect() {
    if (typeof SockJS === "undefined" || typeof Stomp === "undefined") {
        setConnected(false, "실시간 알림 없음");
        return;
    }
    const stomp = Stomp.over(new SockJS("/ws"));
    stomp.debug = null;
    stomp.connect({}, () => {
        reconnectDelay = 2000;
        setConnected(true);
        stomp.subscribe("/topic/anomalies", message => {
            try { onAnomaly(JSON.parse(message.body)); } catch { /* 형식이 다른 메시지는 무시 */ }
        });
    }, () => {
        setConnected(false);
        setTimeout(connect, reconnectDelay);
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
    });
}

/* ---------------- 시작 ---------------- */

initTheme();
initMarket();
initCases({ onNavigate: navigate });
renderRuleHelp();

document.querySelectorAll("[data-status-link]").forEach(link => link.addEventListener("click", event => {
    event.preventDefault();
    setFilters({ status: link.dataset.statusLink, stockCode: "" });
    navigate("cases");
}));
document.addEventListener("click", event => {
    const row = event.target.closest("tr[data-href]");
    if (row) navigate(row.dataset.href);
});
$("quoteCasesLink").addEventListener("click", event => {
    event.preventDefault();
    setFilters({ status: "", stockCode: event.currentTarget.dataset.stock || "" });
    navigate("cases");
});
$("refreshView").addEventListener("click", () => {
    refreshCurrent();
    if (currentView === "cases" && selectedCaseId()) openCase(selectedCaseId());
});
document.addEventListener("keydown", onKeydown);
document.addEventListener("visibilitychange", () => { if (!document.hidden) refreshCurrent(); });
document.addEventListener("marketguard:cases-changed", refreshNewCaseBadge);
window.addEventListener("hashchange", route);
setInterval(() => { if (!document.hidden) refreshNewCaseBadge(); }, 60000);

route();
refreshNewCaseBadge();
connect();
