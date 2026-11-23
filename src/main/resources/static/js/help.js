// 도움말 화면: 규칙 설명과 서버에서 받은 실제 설정값.
import { api } from "./api.js";
import { RULES, escapeHtml, fmtNumber, ruleLabel } from "./format.js";
import { errorLine } from "./ui.js";

const $ = id => document.getElementById(id);

function parameterText(parameter) {
    return `${escapeHtml(parameter.label)} <b>${escapeHtml(fmtNumber(parameter.value))}${escapeHtml(parameter.unit)}</b>`;
}

/** 규칙 설명을 그리고, 설정을 받으면 규칙마다 실제 기준을 덧붙인다. */
export async function renderHelp() {
    const host = $("ruleHelp");
    const draw = settings => {
        const byRule = new Map((settings?.rules || []).map(rule => [rule.ruleType, rule.parameters]));
        host.innerHTML = Object.entries(RULES).map(([type, meta]) => {
            const parameters = byRule.get(type);
            const current = parameters && parameters.length
                ? `<p class="subtle xs" style="margin-top:4px">현재 기준: ${parameters.map(parameterText).join(" · ")}</p>`
                : "";
            return `<dt>${escapeHtml(meta.label)}<span class="mono">${type}</span></dt>` +
                `<dd>${escapeHtml(meta.help)}${current}</dd>`;
        }).join("");
    };
    draw(null);
    try {
        const settings = await api.detectionSettings();
        draw(settings);
        renderCaseSettings(settings);
    } catch (error) {
        $("caseSettings").innerHTML = `<dd style="grid-column:1/-1">${errorLine("현재 설정을 불러오지 못했습니다.", error)}</dd>`;
    }
}

function renderCaseSettings(settings) {
    const cases = settings.cases;
    const weights = cases.ruleWeights
        .filter(weight => weight.points > 0)
        .map(weight => `${escapeHtml(ruleLabel(weight.ruleType))} ${Number(weight.points)}점`)
        .join(" · ");
    const rows = [
        ["알림 간격", `같은 종목·규칙 신호는 ${fmtNumber(settings.cooldownMinutes)}분 안에 다시 기록하지 않습니다.`],
        ["사건 묶음", `같은 종목 신호가 ${fmtNumber(cases.groupingWindowMinutes)}분 안에 이어지면 한 사건으로 묶고, ` +
            `${fmtNumber(cases.inactivityTimeoutMinutes)}분 동안 새 신호가 없으면 자동 종결합니다. ` +
            `종결 후 ${fmtNumber(cases.reactivationWindowMinutes)}분 안에 새 신호가 오면 다시 엽니다.`],
        ["관심도", `점수 ${Number(cases.highThreshold)}점 이상은 높음, ${Number(cases.mediumThreshold)}점 이상은 보통입니다. ` +
            `서로 다른 규칙이 함께 나오면 ${Number(cases.simultaneousSignalBonus)}점을 더하고 최대 ${Number(cases.maxScore)}점입니다.`],
        ["규칙별 점수", weights || "설정된 가중치가 없습니다."]
    ];
    $("caseSettings").innerHTML = rows.map(([label, text]) =>
        `<dt>${escapeHtml(label)}</dt><dd>${escapeHtml(text)}</dd>`).join("");
}
