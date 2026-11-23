// 표시용 라벨·포맷터. 서버에서 온 문자열은 HTML에 넣기 전에 반드시 escapeHtml을 거친다.

export const RULES = {
    PRICE_SPIKE: {
        label: "가격 급변동",
        help: "현재가가 최근 가격 평균보다 기준 비율 이상 오르거나 내린 경우입니다. 짧은 시간의 급격한 움직임을 잡습니다."
    },
    PRICE_LIMIT: {
        label: "가격제한폭 근접",
        help: "하루 최대 변동폭인 상한가·하한가에 도달했거나 설정한 비율 안으로 가까워진 경우입니다."
    },
    ORDERBOOK_IMBALANCE: {
        label: "호가 불균형",
        help: "호가창의 매수 잔량과 매도 잔량 중 한쪽이 기준 배수 이상 많은 경우입니다. 호가는 취소될 수 있어 실제 체결을 뜻하지는 않습니다."
    },
    VOLUME_SURGE: {
        label: "거래량 급증",
        help: "최신 1분봉 거래량이 직전 평균의 기준 배수 이상으로 늘어난 경우입니다."
    },
    PRICE_VOLUME_SURGE: {
        label: "가격·거래량 동시 급증",
        help: "가격 변동과 1분 거래량이 같은 평가에서 각각의 기준을 모두 넘은 경우입니다. 단일 신호보다 우선 확인할 가치가 큽니다."
    },
    INVESTMENT_WARNING: {
        label: "투자경고 지정",
        help: "거래소가 투자경고·투자위험·단기과열·정리매매 종목으로 지정해 현재 유효한 경우입니다."
    },
    INSTITUTIONAL_NET_BUY_SURGE: {
        label: "기관 순매수 급증",
        help: "KOSPI·KOSDAQ 시장 전체의 기관 순매수가 최근 중앙값의 기준 배수와 최소 금액을 모두 넘은 경우입니다. 개별 종목 신호가 아닙니다."
    },
    INSTITUTIONAL_NET_SELL_SURGE: {
        label: "기관 순매도 급증",
        help: "KOSPI·KOSDAQ 시장 전체의 기관 순매도가 최근 중앙값의 기준 배수와 최소 금액을 모두 넘은 경우입니다. 개별 종목 신호가 아닙니다."
    }
};

export const STATUS = {
    NEW: "신규",
    REVIEWING: "검토 중",
    WATCHING: "관찰",
    ESCALATED: "상위 검토",
    DISMISSED: "기각",
    CLOSED: "종결"
};

/** 서버 CaseTransitionPolicy와 동일한 허용 전이. 서버가 최종 검증하므로 화면 안내용이다. */
export const TRANSITIONS = {
    NEW: ["REVIEWING", "WATCHING", "ESCALATED", "DISMISSED", "CLOSED"],
    REVIEWING: ["WATCHING", "ESCALATED", "DISMISSED", "CLOSED"],
    WATCHING: ["REVIEWING", "ESCALATED", "DISMISSED", "CLOSED"],
    ESCALATED: ["REVIEWING", "WATCHING", "DISMISSED", "CLOSED"],
    DISMISSED: ["REVIEWING", "WATCHING", "CLOSED"],
    CLOSED: ["REVIEWING", "WATCHING"]
};
export const TERMINAL_STATUSES = new Set(["DISMISSED", "CLOSED"]);
export const OPEN_STATUSES = ["NEW", "REVIEWING", "WATCHING", "ESCALATED"];

export const CLOSURE_REASONS = {
    MARKET_WIDE_MOVE: "시장 전체 동반 움직임",
    SECTOR_WIDE_MOVE: "동일 업종 동반 움직임",
    OPEN_OR_CLOSE_EFFECT: "장 시작 또는 장 마감 효과",
    EXPECTED_HIGH_VOLATILITY: "예상 가능한 고변동성",
    PUBLIC_EVENT: "확인된 공시 또는 기업 이벤트",
    DATA_ERROR: "데이터 오류",
    TEMPORARY_ORDERBOOK_DISTORTION: "일시적 호가 왜곡",
    NEEDS_FURTHER_REVIEW: "추가 확인 필요",
    INACTIVITY: "활동 없음으로 자동 종결",
    OTHER: "기타"
};

export const SEVERITY = { CRITICAL: "심각", WARNING: "주의", INFO: "정보" };
export const ATTENTION = { HIGH: "높음", MEDIUM: "보통", LOW: "낮음" };

export const MARKET_SYMBOLS = new Set(["KOSPI", "KOSDAQ"]);

export function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>'"]/g, char => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;"
    })[char]);
}

export const ruleLabel = type => RULES[type]?.label || type || "알 수 없는 규칙";
export const statusLabel = status => STATUS[status] || status || "-";

export function severityDot(severity) {
    return `<span class="dot ${escapeHtml(severity)}">${escapeHtml(SEVERITY[severity] || severity || "-")}</span>`;
}

export function statusDot(status) {
    return `<span class="dot ${escapeHtml(status)}">${escapeHtml(statusLabel(status))}</span>`;
}

/** 점수 숫자와 짧은 막대. 관심도(HIGH/MEDIUM)는 색으로만 더하고, 숫자 자체가 주 정보다. */
export function scoreHtml(score, level) {
    const value = Math.max(0, Math.min(100, Number(score) || 0));
    return `<span class="score ${escapeHtml(level)}" title="점수 ${value} / 100">` +
        `<span class="bar"><i style="width:${value}%"></i></span>${value}</span>`;
}

const krw = new Intl.NumberFormat("ko-KR");

export function fmtNumber(value) {
    if (value == null || value === "") return "–";
    const number = Number(value);
    return Number.isFinite(number) ? krw.format(number) : String(value);
}

export function fmtWon(value) {
    return value == null ? "–" : `${fmtNumber(value)}원`;
}

/** 큰 수를 조·억·만 단위로 줄여 읽기 쉽게 표시한다. */
export function fmtCompact(value, unit = "") {
    const v = Number(value);
    if (!Number.isFinite(v) || v <= 0) return "–";
    if (v >= 1e12) return `${(v / 1e12).toFixed(1)}조${unit}`;
    if (v >= 1e8) return `${(v / 1e8).toFixed(1)}억${unit}`;
    if (v >= 1e4) return `${Math.round(v / 1e4).toLocaleString("ko-KR")}만${unit}`;
    return `${krw.format(v)}${unit}`;
}

export function fmtMetric(value, unit) {
    if (value == null) return "해당 없음";
    const number = Number(value);
    const rendered = Number.isFinite(number)
        ? number.toLocaleString("ko-KR", { maximumFractionDigits: 2 })
        : String(value);
    return `${rendered}${unit ? " " + unit : ""}`;
}

/** 등락률 표시. 한국식 색(상승 빨강·하락 파랑)과 화살표를 함께 써 색만으로 구분하지 않는다. */
export function chgHtml(pct) {
    if (pct == null) return `<span class="flat">–</span>`;
    const value = Number(pct);
    const cls = value > 0 ? "up" : value < 0 ? "down" : "flat";
    const arrow = value > 0 ? "▲" : value < 0 ? "▼" : "";
    const sign = value > 0 ? "+" : value < 0 ? "−" : "";
    return `<span class="${cls}">${arrow} ${sign}${Math.abs(value).toFixed(2)}%</span>`;
}

function toDate(iso) {
    if (!iso) return null;
    const date = new Date(iso);
    return Number.isNaN(date.getTime()) ? null : date;
}

export function fmtTime(iso) {
    const d = toDate(iso);
    return d ? d.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit", hourCycle: "h23" }) : "-";
}

export function fmtDateTime(iso) {
    const d = toDate(iso);
    return d ? d.toLocaleString("ko-KR", { month: "numeric", day: "numeric", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }) : "-";
}

/** "3분 전"처럼 상대 시간. 하루가 지나면 날짜로 표시한다. */
export function fmtRelative(iso, now = Date.now()) {
    const d = toDate(iso);
    if (!d) return "-";
    const seconds = Math.round((now - d.getTime()) / 1000);
    if (seconds < 45) return "방금";
    if (seconds < 3600) return `${Math.round(seconds / 60)}분 전`;
    if (seconds < 86400) return `${Math.round(seconds / 3600)}시간 전`;
    return fmtDateTime(iso);
}

export function fmtDuration(seconds) {
    if (seconds == null) return "–";
    const s = Math.round(Number(seconds));
    if (s < 60) return `${s}초`;
    if (s < 3600) return `${Math.round(s / 60)}분`;
    return `${(s / 3600).toFixed(1)}시간`;
}

export function isToday(iso) {
    const d = toDate(iso);
    if (!d) return false;
    const now = new Date();
    return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}

/**
 * 차트 시간 보정. lightweight-charts는 UTC로 그리므로
 * 일봉은 KST 날짜 문자열, 분봉은 KST 벽시계 초로 바꾼다.
 */
export function toBarTime(timestamp, interval) {
    const ms = new Date(timestamp).getTime();
    if (interval === "1d") return new Date(ms + 9 * 3600 * 1000).toISOString().slice(0, 10);
    return Math.floor(ms / 1000) + 9 * 3600;
}

export function stockLabel(name, code) {
    const shown = name && name !== code ? name : code;
    return `<span class="stock">${escapeHtml(shown)}</span><span class="code">${escapeHtml(code)}</span>`;
}
