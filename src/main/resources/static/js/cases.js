// 사건 화면: 목록(필터·페이지) + 상세(근거·차트·검토·메모·이력).
import { api } from "./api.js";
import {
    ATTENTION, CLOSURE_REASONS, MARKET_SYMBOLS, OPEN_STATUSES, RULES, TERMINAL_STATUSES, TRANSITIONS,
    escapeHtml, fmtDateTime, fmtMetric, fmtRelative, ruleLabel, scoreHtml, severityDot, statusDot,
    statusLabel, stockLabel, toBarTime
} from "./format.js";
import { announce, errorLine, loadingLine, stateLine, storage, toast } from "./ui.js";
import { chartsAvailable, createCandleChart } from "./charts.js";

const PAGE_SIZE = 25;
const DEFAULT_FILTERS = { status: "OPEN", stockCode: "", ruleType: "", severity: "", sort: "lastDetectedAt" };

const state = {
    filters: { ...DEFAULT_FILTERS },
    page: 0,
    cases: [],
    selectedId: null,
    detail: null,
    chart: null,
    listRequest: 0,
    detailRequest: 0
};

const $ = id => document.getElementById(id);
let navigate = () => {};

export function initCases({ onNavigate }) {
    navigate = onNavigate;
    $("filterRule").innerHTML = `<option value="">모든 규칙</option>` + Object.entries(RULES)
        .map(([type, meta]) => `<option value="${type}">${escapeHtml(meta.label)}</option>`).join("");
    syncFilterControls();

    $("statusTabs").addEventListener("click", event => {
        const button = event.target.closest("[data-status]");
        if (button) setFilters({ status: button.dataset.status });
    });
    $("filterRule").addEventListener("change", event => setFilters({ ruleType: event.target.value }));
    $("filterSeverity").addEventListener("change", event => setFilters({ severity: event.target.value }));
    $("filterSort").addEventListener("change", event => setFilters({ sort: event.target.value }));
    $("filterStock").addEventListener("input", event => {
        const value = event.target.value.replace(/\D/g, "").slice(0, 6);
        event.target.value = value;
        if (value.length === 0 || value.length === 6) setFilters({ stockCode: value });
    });
    $("casePrev").addEventListener("click", () => { if (state.page > 0) { state.page--; loadCases({ announceResult: true }); } });
    $("caseNext").addEventListener("click", () => { state.page++; loadCases({ announceResult: true }); });
    $("caseList").addEventListener("click", event => {
        const row = event.target.closest("[data-case-id]");
        if (row) navigate(`case-${Number(row.dataset.caseId)}`);
    });
}

export function setFilters(patch) {
    state.filters = { ...state.filters, ...patch };
    state.page = 0;
    syncFilterControls();
    return loadCases({ announceResult: true });
}

function syncFilterControls() {
    document.querySelectorAll("#statusTabs [data-status]").forEach(button =>
        button.setAttribute("aria-pressed", String(button.dataset.status === state.filters.status)));
    $("filterRule").value = state.filters.ruleType;
    $("filterSeverity").value = state.filters.severity;
    $("filterSort").value = state.filters.sort;
    $("filterStock").value = state.filters.stockCode;
}

function queryParams() {
    const { status, stockCode, ruleType, severity, sort } = state.filters;
    const params = { page: state.page, size: PAGE_SIZE, sort, direction: "desc" };
    if (status === "OPEN") params.status = OPEN_STATUSES.join(",");
    else if (status) params.status = status;
    if (stockCode) params.stockCode = stockCode;
    if (ruleType) params.ruleType = ruleType;
    if (severity) params.severity = severity;
    return params;
}

/** announceResult: 사용자가 필터·페이지를 바꿨을 때만 결과 건수를 읽어 준다(주기적 갱신은 조용히). */
export async function loadCases({ announceResult = false } = {}) {
    const request = ++state.listRequest;
    const list = $("caseList");
    if (state.cases.length === 0) list.innerHTML = `<div style="padding:0 16px">${loadingLine()}</div>`;
    try {
        const page = await api.cases(queryParams());
        if (request !== state.listRequest) return;
        state.cases = page.content || [];
        const total = Number(page.totalElements || 0);
        const totalPages = Math.max(Number(page.totalPages || 0), 1);
        state.page = Math.min(Number(page.page || 0), totalPages - 1);
        $("caseCount").textContent = `${total.toLocaleString("ko-KR")}건`;
        $("casePage").textContent = `${state.page + 1} / ${totalPages}`;
        $("casePrev").disabled = state.page <= 0;
        $("caseNext").disabled = state.page + 1 >= totalPages;
        if (announceResult) announce(`사건 ${total.toLocaleString("ko-KR")}건, ${state.page + 1}쪽`);
        list.innerHTML = state.cases.length
            ? state.cases.map(caseRow).join("")
            : `<div style="padding:0 16px">${stateLine(state.filters.status === "OPEN" ? "처리할 사건이 없습니다." : "조건에 맞는 사건이 없습니다.")}</div>`;
    } catch (error) {
        if (request !== state.listRequest) return;
        list.innerHTML = `<div style="padding:0 16px">${errorLine("사건 목록을 불러오지 못했습니다.", error)}</div>`;
    }
}

function caseRow(item) {
    return `<button type="button" class="case-row" data-case-id="${Number(item.id)}" aria-current="${item.id === state.selectedId}">` +
        `<span class="title">${stockLabel(item.stockName, item.stockCode)} · ${escapeHtml(item.title)}</span>` +
        scoreHtml(item.score, item.attentionLevel) +
        `<span class="meta">${statusDot(item.status)}<span>신호 ${Number(item.signalCount)}</span>` +
        `<span class="spacer"></span><time title="${escapeHtml(fmtDateTime(item.lastDetectedAt))}">${escapeHtml(fmtRelative(item.lastDetectedAt))}</time></span>` +
        `</button>`;
}

/** 개요 화면의 '처리 대기' 표에서 쓰는 행. */
export function caseTableRow(item) {
    return `<tr data-href="case-${Number(item.id)}">` +
        `<td>${scoreHtml(item.score, item.attentionLevel)}</td>` +
        `<td><div class="nowrap">${stockLabel(item.stockName, item.stockCode)}</div>` +
        `<div class="subtle xs show-sm">${escapeHtml(item.title)}</div></td>` +
        `<td class="hide-sm">${escapeHtml(item.title)}</td>` +
        `<td class="hide-sm">${statusDot(item.status)}</td>` +
        `<td class="r subtle nowrap">${escapeHtml(fmtRelative(item.lastDetectedAt))}</td></tr>`;
}

/** J/K 이동: 현재 목록에서 앞뒤 사건을 연다. */
export function stepCase(offset) {
    if (state.cases.length === 0) return;
    const index = state.cases.findIndex(item => item.id === state.selectedId);
    const next = state.cases[Math.max(0, Math.min(state.cases.length - 1, index < 0 ? 0 : index + offset))];
    if (next) {
        navigate(`case-${next.id}`);
        document.querySelector(`[data-case-id="${next.id}"]`)?.scrollIntoView({ block: "nearest" });
    }
}

/* ---------------- 상세 ---------------- */

/**
 * userOpened: 사용자가 사건을 연 경우에만 제목으로 포커스를 옮기고 알린다.
 * 새로고침·실시간 갱신으로 다시 그릴 때는 포커스를 빼앗지 않는다.
 */
export async function openCase(id, { userOpened = false } = {}) {
    const request = ++state.detailRequest;
    state.selectedId = id;
    document.querySelectorAll("[data-case-id]").forEach(row =>
        row.setAttribute("aria-current", String(Number(row.dataset.caseId) === id)));
    $("casesLayout").classList.add("showing-detail");
    const host = $("caseDetail");
    if (state.detail?.id !== id) host.innerHTML = `<div class="detail">${loadingLine()}</div>`;
    try {
        const detail = await api.caseDetail(id);
        if (request !== state.detailRequest) return;
        state.detail = detail;
        renderDetail(detail);
        if (userOpened) {
            $("caseHeading")?.focus();
            announce(`사건 ${Number(detail.id)}, ${detail.stockName || detail.stockCode}, ${detail.title}`);
        }
    } catch (error) {
        if (request !== state.detailRequest) return;
        host.innerHTML = `<div class="detail">${crumb(id)}${errorLine("사건을 불러오지 못했습니다.", error)}</div>`;
        bindCrumb();
    }
}

export function closeCaseDetail() {
    state.selectedId = null;
    document.querySelectorAll("[data-case-id]").forEach(row => row.setAttribute("aria-current", "false"));
    $("casesLayout").classList.remove("showing-detail");
}

export const selectedCaseId = () => state.selectedId;

function crumb(id) {
    return `<nav class="crumb" aria-label="위치"><button type="button" data-back>사건</button><span>/</span><span>#${Number(id)}</span></nav>`;
}

function bindCrumb() {
    $("caseDetail").querySelector("[data-back]")?.addEventListener("click", () => navigate("cases"));
}

function renderDetail(detail) {
    const signals = detail.signals || [];
    $("caseDetail").innerHTML = `<div class="detail">` +
        `<header class="detail-top">${crumb(detail.id)}` +
        `<div class="detail-title"><div><h2 id="caseHeading" tabindex="-1">${escapeHtml(detail.stockName || detail.stockCode)}<span class="code">${escapeHtml(detail.stockCode)}</span></h2>` +
        `<p class="lead">${escapeHtml(detail.title)}. ${escapeHtml(detail.summary)}</p></div>` +
        `<div class="big ${escapeHtml(detail.attentionLevel)}" title="관심도 ${escapeHtml(ATTENTION[detail.attentionLevel] || "")}">${Number(detail.score)}<small>/100</small></div></div>` +
        `</header>` +
        propsHtml(detail) +
        reviewHtml(detail) +
        `<section class="section"><div class="section-head"><h2>가격 흐름</h2><span class="subtle xs">1분봉, 신호 위치 표시</span></div>` +
        `<div id="caseChart" class="chart"></div><p id="caseChartMarkerNote" class="subtle xs" hidden></p></section>` +
        `<section class="section"><div class="section-head"><h2>근거</h2><span class="subtle xs">${signals.length}개 신호</span></div>` +
        `<div>${signals.map(signalHtml).join("")}</div></section>` +
        scoreSection(detail) +
        checklistSection(detail) +
        notesSection(detail) +
        historySection(detail) +
        `<p class="footnote">${escapeHtml(detail.caution)}</p>` +
        `</div>`;
    bindDetail(detail);
    loadCaseChart(detail);
}

function propsHtml(detail) {
    const tags = (detail.contextTags || []).map(tag => `<span class="tag">${escapeHtml(tag)}</span>`).join("");
    const closure = detail.closureReason
        ? `<div><dt>종결 사유</dt><dd>${escapeHtml(CLOSURE_REASONS[detail.closureReason] || detail.closureReason)}</dd></div>` : "";
    return `<dl class="props">` +
        `<div><dt>상태</dt><dd>${statusDot(detail.status)}</dd></div>` +
        `<div><dt>담당</dt><dd>${escapeHtml(detail.reviewer || "없음")}</dd></div>` +
        `<div><dt>처음 탐지</dt><dd>${escapeHtml(fmtDateTime(detail.firstDetectedAt))}</dd></div>` +
        `<div><dt>마지막 탐지</dt><dd>${escapeHtml(fmtDateTime(detail.lastDetectedAt))}</dd></div>` +
        closure +
        (tags ? `<div style="grid-column:1/-1"><dt>맥락</dt><dd class="tags" style="margin-top:4px">${tags}</dd></div>` : "") +
        `</dl>`;
}

function reviewHtml(detail) {
    const options = (TRANSITIONS[detail.status] || [])
        .map(status => `<option value="${status}">${escapeHtml(statusLabel(status))}</option>`).join("");
    const reasons = Object.entries(CLOSURE_REASONS).filter(([key]) => key !== "INACTIVITY")
        .map(([key, label]) => `<option value="${key}">${escapeHtml(label)}</option>`).join("");
    return `<section class="section" aria-label="검토"><div class="review">` +
        `<label class="field"><span>상태 변경</span><select id="reviewStatus" class="input">` +
        `<option value="">${escapeHtml(statusLabel(detail.status))} 유지</option>${options}</select></label>` +
        `<label class="field reason" id="reasonField" hidden><span>사유</span><select id="reviewReason" class="input">` +
        `<option value="">선택</option>${reasons}</select></label>` +
        `<label class="field note"><span>근거 메모</span><input id="reviewDetail" class="input" maxlength="500" placeholder="선택 사항"/></label>` +
        `<div class="actions"><button type="button" id="saveStatus" class="btn primary" disabled>저장</button></div>` +
        `</div><p id="statusMessage" class="form-msg" role="status"></p></section>`;
}

function signalHtml(signal) {
    const hit = signal.deviationRatio != null && Number(signal.deviationRatio) >= 1;
    const extra = (signal.measurements || []).map(value =>
        `<div><dt>${escapeHtml(value.label)}</dt><dd>${escapeHtml(fmtMetric(value.observedValue, value.unit))}</dd></div>`).join("");
    return `<article class="signal">` +
        `<div class="signal-head"><b>${escapeHtml(ruleLabel(signal.ruleType))}</b>${severityDot(signal.severity)}` +
        `<time>${escapeHtml(fmtDateTime(signal.detectedAt))}</time></div>` +
        `<p class="summary">${escapeHtml(signal.summary)}</p>` +
        `<p>${escapeHtml(signal.explanation)}</p>` +
        `<dl class="measures">` +
        `<div${hit ? ` class="hit"` : ""}><dt>관측</dt><dd>${escapeHtml(fmtMetric(signal.observedValue, signal.valueUnit))}</dd></div>` +
        `<div><dt>기준</dt><dd>${escapeHtml(fmtMetric(signal.baselineValue, signal.valueUnit))}</dd></div>` +
        `<div><dt>임계</dt><dd>${escapeHtml(fmtMetric(signal.thresholdValue, signal.valueUnit))}</dd></div>` +
        extra + `</dl>` +
        `<p class="subtle xs">비교 범위: ${escapeHtml(signal.lookbackDescription || "없음")}</p>` +
        `</article>`;
}

function scoreSection(detail) {
    const rows = (detail.scoreContributions || []).map(item =>
        `<tr><td>${escapeHtml(ruleLabel(item.ruleType))}</td><td>${escapeHtml(item.summary)}</td>` +
        `<td class="r">${escapeHtml(fmtMetric(item.observedValue, item.unit))}</td><td class="r">+${Number(item.points)}</td></tr>`).join("");
    const bonus = Number(detail.simultaneousSignalBonus) > 0
        ? `<tr><td>동시 발생</td><td class="muted">서로 다른 규칙이 함께 탐지됨</td><td></td><td class="r">+${Number(detail.simultaneousSignalBonus)}</td></tr>` : "";
    return `<section class="section"><div class="section-head"><h2>점수 구성</h2></div>` +
        `<div class="table-wrap"><table class="t"><thead><tr><th>규칙</th><th>근거</th><th class="r">관측값</th><th class="r">점수</th></tr></thead>` +
        `<tbody>${rows}${bonus}<tr><td colspan="3"><b>합계</b></td><td class="r"><b>${Number(detail.score)}</b></td></tr></tbody></table></div></section>`;
}

const checklistKey = id => `marketguard.checks.${Number(id)}`;

function checklistSection(detail) {
    const checks = [...new Set((detail.signals || []).flatMap(signal => signal.recommendedChecks || []))];
    if (checks.length === 0) return "";
    let done = [];
    try { done = JSON.parse(storage.get(checklistKey(detail.id)) || "[]"); } catch { done = []; }
    return `<section class="section"><div class="section-head"><h2>확인 항목</h2>` +
        `<span class="subtle xs">${done.length} / ${checks.length}</span></div>` +
        `<div class="checks" id="caseChecklist">` +
        checks.map((value, index) => `<label><input type="checkbox" data-check="${index}"${done.includes(index) ? " checked" : ""}/>` +
            `<span>${escapeHtml(value)}</span></label>`).join("") +
        `</div></section>`;
}

function notesSection(detail) {
    const notes = (detail.notes || []).slice().reverse().map(note =>
        `<li><time>${escapeHtml(fmtDateTime(note.createdAt))}</time>` +
        `<div><span class="who">${escapeHtml(note.author)}</span><div class="body">${escapeHtml(note.note)}</div></div></li>`).join("");
    return `<section class="section"><div class="section-head"><h2>메모</h2></div>` +
        `<textarea id="caseNote" class="input" maxlength="2000" rows="3" aria-label="새 메모" placeholder="판단 근거, 후속 확인 내용"></textarea>` +
        `<div class="toolbar"><button type="button" id="saveNote" class="btn" disabled>메모 추가</button>` +
        `<span id="noteMessage" class="form-msg" role="status"></span><span class="grow"></span>` +
        `<span id="noteCounter" class="subtle xs num">0 / 2000</span></div>` +
        (notes ? `<ul class="log">${notes}</ul>` : "") + `</section>`;
}

function historySection(detail) {
    const items = (detail.history || []).slice().reverse().map(item =>
        `<li><time>${escapeHtml(fmtDateTime(item.changedAt))}</time>` +
        `<div><span class="who">${escapeHtml(item.actor)}</span> ` +
        `${escapeHtml(item.fromStatus ? statusLabel(item.fromStatus) : "생성")} → ${escapeHtml(statusLabel(item.toStatus))}` +
        `${item.reason ? ` · ${escapeHtml(CLOSURE_REASONS[item.reason] || item.reason)}` : ""}` +
        `${item.detail ? `<div class="body">${escapeHtml(item.detail)}</div>` : ""}</div></li>`).join("");
    return `<section class="section"><div class="section-head"><h2>이력</h2></div><ul class="log">${items}</ul></section>`;
}

const TOKEN_CANCELLED = "OPERATOR_TOKEN_CANCELLED";

function showFormError(element, error) {
    element.className = error.code === TOKEN_CANCELLED ? "form-msg" : "form-msg error";
    element.textContent = error.code === TOKEN_CANCELLED ? "토큰을 입력하지 않아 저장하지 않았습니다." : error.message;
}

function bindDetail(detail) {
    bindCrumb();
    const status = $("reviewStatus");
    const reason = $("reviewReason");
    const save = $("saveStatus");
    const refresh = () => {
        const terminal = TERMINAL_STATUSES.has(status.value);
        $("reasonField").hidden = !terminal;
        if (!terminal) reason.value = "";
        save.disabled = !status.value || (terminal && !reason.value);
    };
    status.addEventListener("change", refresh);
    reason.addEventListener("change", refresh);
    save.addEventListener("click", async () => {
        const message = $("statusMessage");
        const target = status.value;
        save.disabled = true;
        message.className = "form-msg";
        message.textContent = "저장 중…";
        try {
            await api.updateStatus(detail.id, {
                status: target,
                version: detail.version,
                reason: reason.value || null,
                detail: $("reviewDetail").value.trim() || null
            });
            toast(`상태 변경: ${statusLabel(target)}`, { body: `사건 #${detail.id}` });
            document.dispatchEvent(new CustomEvent("marketguard:cases-changed"));
            await Promise.all([loadCases(), openCase(detail.id)]);
        } catch (error) {
            showFormError(message, error);
            if (error.code === "CASE_VERSION_CONFLICT") openCase(detail.id);
            refresh();
        }
    });

    const note = $("caseNote");
    const saveNote = $("saveNote");
    note.addEventListener("input", () => {
        $("noteCounter").textContent = `${note.value.length} / 2000`;
        saveNote.disabled = note.value.trim().length === 0;
    });
    saveNote.addEventListener("click", async () => {
        const message = $("noteMessage");
        saveNote.disabled = true;
        message.className = "form-msg";
        message.textContent = "저장 중…";
        try {
            await api.addNote(detail.id, note.value.trim());
            toast("메모 추가됨", { body: `사건 #${detail.id}` });
            await openCase(detail.id);
        } catch (error) {
            showFormError(message, error);
            saveNote.disabled = false;
        }
    });

    $("caseChecklist")?.addEventListener("change", () => {
        const done = [...document.querySelectorAll("#caseChecklist [data-check]:checked")].map(input => Number(input.dataset.check));
        storage.set(checklistKey(detail.id), JSON.stringify(done));
    });
}

/* ---------------- 사건 차트 ---------------- */

function markerTimeForSignal(timestamp, barTimes) {
    const observedAt = toBarTime(timestamp, "1m");
    if (!Number.isFinite(observedAt)) return null;
    // 토스 1분봉 timestamp는 봉 종료 시각이다. 신호 직후 60초 안에 끝나는 실제 봉에만 연결한다.
    return barTimes.find(time => time >= observedAt && time - observedAt <= 60) ?? null;
}

/** 오래된 사건이면 마지막 신호 1시간 뒤까지의 봉을 요청해 신호 전후가 보이게 한다. */
function caseChartBefore(detail) {
    const observedTimes = (detail.signals || [])
        .map(signal => new Date(signal.marketObservedAt || signal.detectedAt).getTime())
        .filter(Number.isFinite);
    if (observedTimes.length === 0) return null;
    const latestObservedAt = Math.max(...observedTimes);
    if (Date.now() - latestObservedAt <= 60 * 60 * 1000) return null;
    return new Date(latestObservedAt + 60 * 60 * 1000).toISOString();
}

function chartMessage(el, text) {
    el.style.height = "auto";
    el.innerHTML = stateLine(text);
}

async function loadCaseChart(detail) {
    const el = $("caseChart");
    if (state.chart) { state.chart.remove(); state.chart = null; }
    if (MARKET_SYMBOLS.has(detail.stockCode)) return chartMessage(el, "시장 전체 신호라 종목 차트가 없습니다.");
    if (!chartsAvailable()) return chartMessage(el, "차트 라이브러리를 불러오지 못했습니다.");
    try {
        const context = await api.stockContext(detail.stockCode, caseChartBefore(detail));
        if (state.detail?.id !== detail.id || !document.body.contains(el)) return;
        state.chart = createCandleChart(el);
        const barTimes = state.chart.setCandles(context.candles, "1m");
        if (barTimes.length === 0) {
            state.chart.remove();
            state.chart = null;
            return chartMessage(el, "이 시간대의 1분봉이 없습니다.");
        }
        const markers = (detail.signals || []).map(signal => {
            const time = markerTimeForSignal(signal.marketObservedAt || signal.detectedAt, barTimes);
            return time == null ? null : { time, position: "aboveBar", shape: "arrowDown", text: ruleLabel(signal.ruleType) };
        }).filter(marker => marker !== null).sort((a,b) => a.time-b.time);
        state.chart.setMarkers(markers);
        const omittedCount = (detail.signals || []).length - markers.length;
        const markerNote = $("caseChartMarkerNote");
        if (markerNote && omittedCount > 0) {
            markerNote.textContent = `신호 ${omittedCount}개는 조회된 120개 1분봉 범위 밖이라 차트에 표시하지 않았습니다.`;
            markerNote.hidden = false;
        }
    } catch (error) {
        chartMessage(el, `가격 흐름을 불러오지 못했습니다. ${error.message}`);
    }
}
