// lightweight-charts 래퍼. 색은 CSS 토큰에서 읽어 밝은/어두운 테마를 모두 따른다.
import { onThemeChange, token } from "./ui.js";
import { toBarTime } from "./format.js";

const live = new Set();

function palette() {
    return {
        text: token("--fg-3"),
        grid: token("--line"),
        border: token("--line-strong"),
        up: token("--up"),
        down: token("--down"),
        upVolume: token("--chart-up-volume"),
        downVolume: token("--chart-down-volume"),
        marker: token("--warning")
    };
}

function chartOptions(colors) {
    return {
        layout: { background: { color: "transparent" }, textColor: colors.text, fontFamily: token("--font-sans") },
        grid: { vertLines: { color: colors.grid }, horzLines: { color: colors.grid } },
        rightPriceScale: { borderColor: colors.border },
        timeScale: { borderColor: colors.border }
    };
}

onThemeChange(() => live.forEach(chart => chart.restyle()));

export const chartsAvailable = () => typeof LightweightCharts !== "undefined";

/** 캔들 + 거래량 차트를 만든다. 컨테이너 크기를 따라가며, remove()로 정리한다. */
export function createCandleChart(container, { timeVisible = true } = {}) {
    if (!chartsAvailable()) return null;
    let colors = palette();
    const chart = LightweightCharts.createChart(container, {
        ...chartOptions(colors),
        width: container.clientWidth,
        height: container.clientHeight || 260,
        timeScale: { ...chartOptions(colors).timeScale, timeVisible, secondsVisible: false }
    });
    const candles = chart.addCandlestickSeries({
        upColor: colors.up, downColor: colors.down, borderVisible: false,
        wickUpColor: colors.up, wickDownColor: colors.down
    });
    const volume = chart.addHistogramSeries({ priceFormat: { type: "volume" }, priceScaleId: "" });
    volume.priceScale().applyOptions({ scaleMargins: { top: 0.8, bottom: 0 } });

    let lastRows = [];
    const resize = new ResizeObserver(() => chart.applyOptions({ width: container.clientWidth }));
    resize.observe(container);

    const handle = {
        /** 원본 캔들 배열을 그린다. 같은 시각의 중복 봉은 마지막 값을 쓴다. 그린 봉의 시각 배열을 돌려준다. */
        setCandles(rawCandles, interval) {
            const byTime = new Map();
            (rawCandles || []).forEach(c => byTime.set(toBarTime(c.timestamp, interval), c));
            lastRows = [...byTime.entries()].sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
            paint();
            chart.timeScale().fitContent();
            return lastRows.map(([time]) => time);
        },
        setMarkers(markers) {
            candles.setMarkers(markers.map(m => ({ ...m, color: colors.marker })));
        },
        restyle() {
            colors = palette();
            chart.applyOptions(chartOptions(colors));
            candles.applyOptions({ upColor: colors.up, downColor: colors.down, wickUpColor: colors.up, wickDownColor: colors.down });
            paint();
        },
        remove() {
            resize.disconnect();
            live.delete(handle);
            chart.remove();
        }
    };

    function paint() {
        candles.setData(lastRows.map(([time, c]) => ({ time, open: +c.open, high: +c.high, low: +c.low, close: +c.close })));
        volume.setData(lastRows.map(([time, c]) => ({
            time, value: Number(c.volume) || 0, color: +c.close >= +c.open ? colors.upVolume : colors.downVolume
        })));
    }

    live.add(handle);
    return handle;
}
