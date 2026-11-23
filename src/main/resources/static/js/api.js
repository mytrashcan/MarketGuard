// 서버 API 호출. 읽기는 getJson, 쓰기는 CSRF 토큰과(필요하면) 운영자 토큰을 붙이는 writeApi를 쓴다.
import { askOperatorToken, storage } from "./ui.js";

const OPERATOR_TOKEN_KEY = "marketguard.operatorToken";

const ERROR_MESSAGES = {
    INVALID_REQUEST: "입력값을 확인해 주세요.",
    CASE_NOT_FOUND: "사건을 찾을 수 없습니다. 목록을 새로고침해 주세요.",
    ANOMALY_NOT_FOUND: "신호를 찾을 수 없습니다.",
    CASE_VERSION_CONFLICT: "다른 사람이 먼저 수정했습니다. 최신 내용을 불러왔으니 다시 확인 후 저장해 주세요.",
    MARKET_DATA_UNAVAILABLE: "시세 데이터를 잠시 가져올 수 없습니다. 잠시 후 다시 시도해 주세요.",
    INVALID_MARKET_DATA_RESPONSE: "시세 제공처의 응답이 올바르지 않습니다.",
    AUTHENTICATION_REQUIRED: "로그인이 필요합니다.",
    RATE_LIMITED: "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.",
    NETWORK: "서버에 연결할 수 없습니다. 서버가 실행 중인지, 네트워크가 연결됐는지 확인해 주세요."
};

export class ApiError extends Error {
    constructor(status, code) {
        super(ERROR_MESSAGES[code] || (status === 429 ? ERROR_MESSAGES.RATE_LIMITED : "요청을 처리하지 못했습니다."));
        this.status = status;
        this.code = code;
    }
}

async function toError(response) {
    const body = await response.json().catch(() => ({}));
    return new ApiError(response.status, body.code);
}

/** fetch가 네트워크 단계에서 실패하면(서버 중지·오프라인) 화면용 오류로 바꾼다. */
async function request(url, options) {
    try {
        return await fetch(url, options);
    } catch {
        throw new ApiError(0, "NETWORK");
    }
}

export async function getJson(url) {
    const response = await request(url, { headers: { Accept: "application/json" } });
    if (!response.ok) throw await toError(response);
    return response.json();
}

let csrfPromise = null;
function csrfToken() {
    if (!csrfPromise) {
        csrfPromise = getJson("/api/csrf").catch(error => { csrfPromise = null; throw error; });
    }
    return csrfPromise;
}

const isAuthFailure = response => response.status === 401 || response.status === 403;

/** 상태 변경·메모 작성 요청. 운영자 토큰이 필요하면 대화상자로 묻고 이 브라우저에 저장한다. */
export async function writeApi(url, method, body) {
    const csrf = await csrfToken();
    const send = operatorToken => request(url, {
        method,
        headers: {
            "Content-Type": "application/json",
            Accept: "application/json",
            [csrf.headerName]: csrf.token,
            ...(operatorToken ? { "X-Operator-Token": operatorToken } : {})
        },
        body: JSON.stringify(body)
    });

    let operatorToken = storage.get(OPERATOR_TOKEN_KEY);
    let response = await send(operatorToken);
    let rejected = Boolean(operatorToken) && isAuthFailure(response);
    while (isAuthFailure(response)) {
        if (rejected) storage.set(OPERATOR_TOKEN_KEY, null);
        operatorToken = await askOperatorToken(rejected);
        if (!operatorToken) throw new ApiError(401, "OPERATOR_TOKEN_CANCELLED");
        response = await send(operatorToken);
        rejected = isAuthFailure(response);
        if (!rejected) storage.set(OPERATOR_TOKEN_KEY, operatorToken);
    }
    if (!response.ok) throw await toError(response);
    return response.json();
}

export const api = {
    rankings: (type, limit = 50) => getJson(`/api/rankings?type=${encodeURIComponent(type)}&limit=${limit}`),
    candles: (code, interval, count = 120) =>
        getJson(`/api/stocks/${encodeURIComponent(code)}/candles?interval=${interval}&count=${count}`),
    stockContext: (code, before) => getJson(`/api/stocks/${encodeURIComponent(code)}/context?count=120` +
        (before ? `&before=${encodeURIComponent(before)}` : "")),
    anomalies: (limit = 50) => getJson(`/api/anomalies?limit=${limit}`),
    signalCount: since => getJson(`/api/anomalies/count?since=${encodeURIComponent(since)}`),
    cases: params => getJson(`/api/cases?${new URLSearchParams(params)}`),
    caseDetail: id => getJson(`/api/cases/${Number(id)}`),
    ruleAnalytics: () => getJson("/api/analytics/rules"),
    audit: (limit = 100) => getJson(`/api/audit?limit=${limit}`),
    updateStatus: (id, payload) => writeApi(`/api/cases/${Number(id)}/status`, "PATCH", payload),
    addNote: (id, note) => writeApi(`/api/cases/${Number(id)}/notes`, "POST", { note })
};
