// 테마, 토스트, 운영자 토큰 대화상자처럼 화면 전체에서 쓰는 UI 도구.
import { escapeHtml } from "./format.js";

const THEME_KEY = "marketguard.theme";

function storageGet(key) {
    try { return localStorage.getItem(key); } catch { return null; }
}
function storageSet(key, value) {
    try {
        if (value == null) localStorage.removeItem(key); else localStorage.setItem(key, value);
    } catch { /* 저장소를 쓸 수 없는 환경이면 이번 세션에만 적용 */ }
}
export const storage = { get: storageGet, set: storageSet };

const themeListeners = [];

export function currentTheme() {
    const explicit = document.documentElement.dataset.theme;
    if (explicit) return explicit;
    return matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

export function onThemeChange(listener) {
    themeListeners.push(listener);
}

function applyTheme(theme) {
    if (theme) document.documentElement.dataset.theme = theme;
    else delete document.documentElement.dataset.theme;
    const icon = document.getElementById("themeIcon");
    const dark = currentTheme() === "dark";
    icon?.setAttribute("href", dark ? "#i-sun" : "#i-moon");
    document.getElementById("themeToggle")?.setAttribute("aria-label", dark ? "밝은 화면으로 전환" : "어두운 화면으로 전환");
    themeListeners.forEach(listener => listener());
}

export function initTheme() {
    applyTheme(storageGet(THEME_KEY));
    document.getElementById("themeToggle").addEventListener("click", () => {
        const next = currentTheme() === "dark" ? "light" : "dark";
        storageSet(THEME_KEY, next);
        applyTheme(next);
    });
    matchMedia("(prefers-color-scheme: dark)").addEventListener("change", () => {
        if (!document.documentElement.dataset.theme) applyTheme(null);
    });
}

/** CSS 토큰 값을 읽는다(차트처럼 CSS를 쓰지 않는 라이브러리에 색을 넘길 때). */
export function token(name) {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

/** 화면 하단 알림. action은 {label, onClick} 하나만 받는다. */
export function toast(title, { body = "", action = null, error = false, timeout = 5000 } = {}) {
    const host = document.getElementById("toasts");
    const el = document.createElement("div");
    el.className = "toast" + (error ? " error" : "");
    el.setAttribute("role", error ? "alert" : "status");
    el.innerHTML = `<div class="body"><div class="title">${escapeHtml(title)}</div>` +
        (body ? `<p>${escapeHtml(body)}</p>` : "") + `</div>`;
    const close = () => el.remove();
    if (action) {
        const button = document.createElement("button");
        button.type = "button";
        button.textContent = action.label;
        button.addEventListener("click", () => { action.onClick(); close(); });
        el.append(button);
    }
    const dismiss = document.createElement("button");
    dismiss.type = "button";
    dismiss.className = "x";
    dismiss.setAttribute("aria-label", "닫기");
    dismiss.textContent = "✕";
    dismiss.addEventListener("click", close);
    el.append(dismiss);
    host.prepend(el);
    while (host.children.length > 3) host.lastElementChild.remove();
    setTimeout(close, error ? timeout * 2 : timeout);
}

/** 운영자 토큰 입력 대화상자. 입력값 또는 취소 시 null을 돌려준다. */
export function askOperatorToken(previousRejected = false) {
    const dialog = document.getElementById("tokenDialog");
    const form = document.getElementById("tokenForm");
    const input = document.getElementById("tokenInput");
    const error = document.getElementById("tokenError");
    input.value = "";
    error.hidden = !previousRejected;
    error.textContent = previousRejected ? "입력한 토큰이 거부되었습니다. 다시 확인해 주세요." : "";
    return new Promise(resolve => {
        const finish = value => {
            form.removeEventListener("submit", onSubmit);
            document.getElementById("tokenCancel").removeEventListener("click", onCancel);
            dialog.removeEventListener("cancel", onCancel);
            if (dialog.open) dialog.close();
            resolve(value);
        };
        const onSubmit = event => {
            event.preventDefault();
            const value = input.value.trim();
            if (value) finish(value);
        };
        const onCancel = event => { event?.preventDefault(); finish(null); };
        form.addEventListener("submit", onSubmit);
        document.getElementById("tokenCancel").addEventListener("click", onCancel);
        dialog.addEventListener("cancel", onCancel);
        dialog.showModal();
        input.focus();
    });
}

/** 한 줄짜리 상태 문구(불러오는 중·비어 있음·오류). hint는 HTML이므로 서버 값은 호출하는 쪽에서 escape 한다. */
export function stateLine(text, { hint = "", error = false } = {}) {
    return `<p class="state-line${error ? " error" : ""}">${escapeHtml(text)}${hint ? ` <span>${hint}</span>` : ""}</p>`;
}

export const loadingLine = () => stateLine("불러오는 중…");

/** message는 완결된 문장("사건 목록을 불러오지 못했습니다.")으로 넘긴다. */
export function errorLine(message, error) {
    return stateLine(`${message} ${error?.message || ""}`.trim(), { error: true });
}

/**
 * 화면 읽기 프로그램에 짧은 상태 문장을 알린다. 큰 영역 전체를 live region으로 두면
 * 다시 그릴 때마다 내용 전체를 읽어 버리므로, 알릴 내용만 이 한 곳으로 보낸다.
 */
export function announce(text) {
    const region = document.getElementById("srStatus");
    if (!region) return;
    region.textContent = "";
    // 같은 문장을 연달아 보내도 다시 읽히도록 잠시 뒤에 넣는다. requestAnimationFrame은
    // 백그라운드 탭에서 멈추므로 쓰지 않는다.
    setTimeout(() => { region.textContent = text; }, 50);
}
