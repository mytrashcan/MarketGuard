#!/usr/bin/env python3
"""Analyze /api/analytics/price-paths.csv exactly as docs/analysis-plan.md specifies.

Standard library only. Usage:
    python3 scripts/analyze_price_paths.py --from 2026-12-01 --to 2026-12-29 --sessions 20 \
        price-paths.csv > results.md

--from and --to are the registered first and last session dates (KST, inclusive). Rows outside them are
ignored, so the download may cover more days than the run. --sessions is the number of regular sessions
collected according to the run record; below 20 no rule gets a confirmatory verdict.

The confirmatory section implements the pre-registered primary analysis. Everything under
"Exploratory" is descriptive and must not be used to claim that a rule works.
"""

from __future__ import annotations

import argparse
import csv
import math
import random
import sys
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation

KST = timezone(timedelta(hours=9))

# Pre-registered parameters (docs/analysis-plan.md). Changing any of these is a plan amendment.
CONFIRMATORY_RULES = ("PRICE_SPIKE", "PRICE_LIMIT", "ORDERBOOK_IMBALANCE", "VOLUME_SURGE", "PRICE_VOLUME_SURGE")
PRIMARY_OFFSET = 30
ALPHA = 0.05
MINIMUM_SAMPLES = 100
MINIMUM_DAYS = 10
MINIMUM_SESSIONS = 20      # collected regular sessions in the run record
CONTINUATION_PERCENT = 55  # rate >= 55% (compared exactly, as 100k >= 55n)
REVERSAL_PERCENT = 45      # rate <= 45%
BOOTSTRAP_RESAMPLES = 10_000
BOOTSTRAP_SEED = 20261124
UPWARD = {"UP", "BUY"}
DOWNWARD = {"DOWN", "SELL"}
EXPLORATORY_OFFSETS = (5, 15, 30, 60)
REQUIRED_COLUMNS = {"anomaly_id", "detected_at", "stock_code", "rule_type", "direction", "offset_minutes",
                    "change_percent", "market_change_percent", "sector_change_percent"}


@dataclass(frozen=True)
class Point:
    anomaly_id: int
    detected_at: datetime
    stock_code: str
    rule_type: str
    direction: str
    offset: int | None  # None: the signal has no recorded price point at all
    change: Decimal | None
    market: Decimal | None
    sector: Decimal | None

    @property
    def day(self) -> date:
        return self.detected_at.astimezone(KST).date()


def _decimal(value: str) -> Decimal | None:
    if value == "":
        return None
    try:
        return Decimal(value)
    except InvalidOperation as error:
        raise ValueError(f"not a number: {value!r}") from error


def read_points(lines) -> list[Point]:
    """Reads the export. Fails on a missing header so an empty or error download never looks like data."""
    reader = csv.DictReader(lines)
    missing = REQUIRED_COLUMNS.difference(reader.fieldnames or [])
    if missing:
        raise ValueError(f"not a price path export, missing columns: {', '.join(sorted(missing))}")
    points = []
    for row in reader:
        points.append(Point(
            anomaly_id=int(row["anomaly_id"]),
            detected_at=datetime.fromisoformat(row["detected_at"].replace("Z", "+00:00")),
            stock_code=row["stock_code"].lstrip("'"),
            rule_type=row["rule_type"],
            direction=row["direction"],
            offset=int(row["offset_minutes"]) if row["offset_minutes"] else None,
            change=_decimal(row["change_percent"]),
            market=_decimal(row["market_change_percent"]),
            sector=_decimal(row["sector_change_percent"]),
        ))
    return points


def within_run(points: list[Point], first_day: date, last_day: date) -> list[Point]:
    return [point for point in points if first_day <= point.day <= last_day]


def is_directional(direction: str) -> bool:
    return direction in UPWARD or direction in DOWNWARD


def first_signal_per_stock_day(points: list[Point]) -> dict[int, list[Point]]:
    """One signal per (stock, rule, KST day): the earliest directional one, ties broken by the lowest id.

    Signals without a direction are outside the analysis and never take the slot. The choice ignores
    outcomes: a first signal without a usable value (including one exported without any price point)
    drops the stock-day instead of being replaced by a later signal that happens to have one.
    """
    by_signal: dict[int, list[Point]] = {}
    for point in points:
        if is_directional(point.direction):
            by_signal.setdefault(point.anomaly_id, []).append(point)
    first: dict[tuple[str, str, date], tuple[datetime, int]] = {}
    for anomaly_id, signal_points in by_signal.items():
        sample = signal_points[0]
        key = (sample.stock_code, sample.rule_type, sample.day)
        candidate = (sample.detected_at, anomaly_id)
        if key not in first or candidate < first[key]:
            first[key] = candidate
    return {anomaly_id: by_signal[anomaly_id] for _, anomaly_id in first.values()}


def followed(direction: str, move: Decimal) -> bool | None:
    """Whether a move went the signal's way; None for no direction or an exact zero (a tie)."""
    if move == 0 or not is_directional(direction):
        return None
    return (move > 0) == (direction in UPWARD)


def adjusted_move(point: Point, adjustment: str) -> Decimal | None:
    if point.change is None:
        return None
    if adjustment == "raw":
        return point.change
    benchmark = point.market if adjustment == "market" else point.sector
    return None if benchmark is None else point.change - benchmark


def outcomes_by_day(signals: dict[int, list[Point]], rule: str, offset: int,
                    adjustment: str) -> dict[date, list[bool]]:
    results: dict[date, list[bool]] = {}
    for signal_points in signals.values():
        if signal_points[0].rule_type != rule:
            continue
        point = next((p for p in signal_points if p.offset == offset), None)
        if point is None:
            continue
        move = adjusted_move(point, adjustment)
        result = None if move is None else followed(point.direction, move)
        if result is not None:
            results.setdefault(point.day, []).append(result)
    return results


def outcomes(signals: dict[int, list[Point]], rule: str, offset: int, adjustment: str) -> list[bool]:
    by_day = outcomes_by_day(signals, rule, offset, adjustment)
    return [result for day in sorted(by_day) for result in by_day[day]]


def day_sign_flip_p(days: list[tuple[int, int]]) -> float:
    """Exact two-sided sign-flip test of a 50% follow rate with trading days as clusters.

    days holds (followed, total) per day. Each day contributes d = 2*followed - total. Under the null the
    sign of each day's whole contribution is equally likely to be + or -, whatever the correlation among
    stocks on that day, so the p-value is the share of the 2^G sign assignments whose |sum| is at least
    the observed |sum|. Counted exactly by dynamic programming over integer sums.
    """
    contributions = [2 * k - n for k, n in days]
    observed = abs(sum(contributions))
    counts = {0: 1}
    for value in contributions:
        shifted: dict[int, int] = {}
        for total, ways in counts.items():
            shifted[total + value] = shifted.get(total + value, 0) + ways
            shifted[total - value] = shifted.get(total - value, 0) + ways
        counts = shifted
    extreme = sum(ways for total, ways in counts.items() if abs(total) >= observed)
    return extreme / 2 ** len(contributions)


def _regularized_beta(x: float, a: float, b: float) -> float:
    """I_x(a, b) by the continued fraction in Numerical Recipes (betacf), for the Student t tail."""
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    log_front = math.lgamma(a + b) - math.lgamma(a) - math.lgamma(b) + a * math.log(x) + b * math.log1p(-x)
    if x > (a + 1) / (a + b + 2):
        return 1.0 - _regularized_beta(1 - x, b, a)
    tiny, c, d = 1e-300, 1.0, 1.0 - (a + b) * x / (a + 1)
    d = 1.0 / (d if abs(d) > tiny else tiny)
    fraction = d
    for m in range(1, 300):
        for numerator in (m * (b - m) * x / ((a + 2 * m - 1) * (a + 2 * m)),
                          -(a + m) * (a + b + m) * x / ((a + 2 * m) * (a + 2 * m + 1))):
            d = 1.0 + numerator * d
            d = 1.0 / (d if abs(d) > tiny else tiny)
            c = 1.0 + numerator / c
            c = c if abs(c) > tiny else tiny
            fraction *= c * d
        if abs(c * d - 1.0) < 1e-12:
            break
    return math.exp(log_front) * fraction / a


def student_t_two_sided_p(t: float, df: int) -> float:
    return _regularized_beta(df / (df + t * t), df / 2, 0.5)


def day_clustered_t_p(days: list[tuple[int, int]]) -> float:
    """Two-sided test of a pooled 50% follow rate with a day-clustered (CR1) standard error, G-1 df.

    Unlike the sign-flip test it does not assume each day's imbalance is symmetric; it relies on the
    average over days being roughly normal, so it is approximate with few days. A run whose days show no
    variation at all has no usable standard error and is treated as no evidence (p = 1).
    """
    groups = len(days)
    if groups < 2:
        return 1.0
    total = sum(n for _, n in days)
    rate = sum(k for k, _ in days) / total
    residual_squares = sum((k - rate * n) ** 2 for k, n in days)
    standard_error = math.sqrt(groups / (groups - 1) * residual_squares) / total
    if standard_error == 0:
        return 1.0
    return student_t_two_sided_p((rate - 0.5) / standard_error, groups - 1)


def day_bootstrap_interval(days: list[tuple[int, int]], resamples: int = BOOTSTRAP_RESAMPLES,
                           seed: int = BOOTSTRAP_SEED) -> tuple[float, float]:
    """95% percentile interval of the pooled rate, resampling whole trading days (fixed seed)."""
    if not days:
        return (math.nan, math.nan)
    generator = random.Random(seed)
    rates = []
    for _ in range(resamples):
        sample = [days[generator.randrange(len(days))] for _ in days]
        total = sum(n for _, n in sample)
        rates.append(sum(k for k, _ in sample) / total)
    rates.sort()
    return (rates[int(0.025 * resamples)], rates[int(0.975 * resamples) - 1])


def holm(p_values: dict[str, float]) -> dict[str, float]:
    ordered = sorted(p_values.items(), key=lambda item: item[1])
    adjusted, running = {}, 0.0
    for index, (name, p) in enumerate(ordered):
        running = max(running, min(1.0, (len(ordered) - index) * p))
        adjusted[name] = running
    return adjusted


def verdict(followed_count: int, n: int, day_count: int, adjusted_p: float | None,
            adjusted_t_p: float | None = None, sessions: int = MINIMUM_SESSIONS) -> str:
    """Both day-level tests must pass Holm; the t-test p defaults to the sign-flip p when not given."""
    if sessions < MINIMUM_SESSIONS:
        return "insufficient (run shorter than 20 sessions)"
    if n < MINIMUM_SAMPLES or day_count < MINIMUM_DAYS:
        return "insufficient"
    adjusted_t_p = adjusted_p if adjusted_t_p is None else adjusted_t_p
    if adjusted_p is None or adjusted_p >= ALPHA or adjusted_t_p is None or adjusted_t_p >= ALPHA:
        return "no evidence"
    # Exact integer comparison: a float 1 - 0.55 would put exactly 45% on the wrong side.
    if 100 * followed_count >= CONTINUATION_PERCENT * n:
        return "continuation"
    if 100 * followed_count <= REVERSAL_PERCENT * n:
        return "reversal"
    return "no evidence (significant, below practical threshold)"


def confirmatory(signals: dict[int, list[Point]], sessions: int = MINIMUM_SESSIONS) -> list[dict]:
    rows = []
    for rule in CONFIRMATORY_RULES:
        by_day = outcomes_by_day(signals, rule, PRIMARY_OFFSET, "market")
        days = [(sum(results), len(results)) for _, results in sorted(by_day.items())]
        n, k = sum(total for _, total in days), sum(hits for hits, _ in days)
        testable = n >= MINIMUM_SAMPLES and len(days) >= MINIMUM_DAYS
        rows.append({"rule": rule, "n": n, "k": k, "days": len(days), "rate": k / n if n else math.nan,
                     "ci": day_bootstrap_interval(days), "p": day_sign_flip_p(days) if testable else None,
                     "p_t": day_clustered_t_p(days) if testable else None})
    adjusted = holm({row["rule"]: row["p"] for row in rows if row["p"] is not None})
    adjusted_t = holm({row["rule"]: row["p_t"] for row in rows if row["p_t"] is not None})
    for row in rows:
        row["p_holm"] = adjusted.get(row["rule"])
        row["p_t_holm"] = adjusted_t.get(row["rule"])
        row["verdict"] = verdict(row["k"], row["n"], row["days"], row["p_holm"], row["p_t_holm"], sessions)
    return rows


def _pct(value: float) -> str:
    return "–" if math.isnan(value) else f"{value * 100:.1f}%"


def _p(value: float | None) -> str:
    return "–" if value is None else f"{value:.4f}"


def report(points: list[Point], first_day: date, last_day: date, sessions: int = MINIMUM_SESSIONS) -> str:
    run_points = within_run(points, first_day, last_day)
    signals = first_signal_per_stock_day(run_points)
    observed_days = sorted({p.day for p in run_points})
    lines = [
        "# MarketGuard price path analysis",
        "",
        f"- Registered run: {first_day} – {last_day} (KST), {sessions} collected sessions per the run record. "
        f"Rows outside it ignored: {len(points) - len(run_points)}",
        f"- Input in run: {len(run_points)} rows, {len({p.anomaly_id for p in run_points})} signals, "
        f"{len(signals)} directional stock-days, {len(observed_days)} days with signals"
        + (f" (first {observed_days[0]}, last {observed_days[-1]})" if observed_days else ""),
        f"- Plan: docs/analysis-plan.md (offset {PRIMARY_OFFSET} min, market-adjusted, day sign-flip and "
        f"day-clustered t tests both under Holm α={ALPHA}, ≥ {MINIMUM_SESSIONS} sessions, n ≥ {MINIMUM_SAMPLES} "
        f"on ≥ {MINIMUM_DAYS} days, practical ≥ {CONTINUATION_PERCENT}% / ≤ {REVERSAL_PERCENT}%)",
        "",
        "## Confirmatory",
        "",
        "| Rule | Stock-days | Days | Followed | Rate | 95% CI (days resampled) | p sign-flip (Holm) "
        "| p clustered t (Holm) | Verdict |",
        "|---|---:|---:|---:|---:|---|---:|---:|---|",
    ]
    for row in confirmatory(signals, sessions):
        low, high = row["ci"]
        lines.append(f"| `{row['rule']}` | {row['n']} | {row['days']} | {row['k']} | {_pct(row['rate'])} | "
                     f"{_pct(low)} – {_pct(high)} | {_p(row['p'])} ({_p(row['p_holm'])}) | "
                     f"{_p(row['p_t'])} ({_p(row['p_t_holm'])}) | {row['verdict']} |")
    lines += [
        "",
        "## Exploratory (descriptive only, not evidence)",
        "",
        "| Rule | Offset | Raw | Market-adjusted | Sector-adjusted |",
        "|---|---:|---|---|---|",
    ]
    for rule in CONFIRMATORY_RULES:
        for offset in EXPLORATORY_OFFSETS:
            cells = []
            for adjustment in ("raw", "market", "sector"):
                results = outcomes(signals, rule, offset, adjustment)
                cells.append(f"{_pct(sum(results) / len(results)) if results else '–'} (n={len(results)})")
            lines.append(f"| `{rule}` | {offset} | " + " | ".join(cells) + " |")
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--from", dest="first_day", required=True, type=date.fromisoformat,
                        help="registered first session date, KST (YYYY-MM-DD)")
    parser.add_argument("--to", dest="last_day", required=True, type=date.fromisoformat,
                        help="registered last session date, KST (YYYY-MM-DD)")
    parser.add_argument("--sessions", required=True, type=int,
                        help="regular sessions collected in the run, from the run record (days without signals count)")
    parser.add_argument("csv", help="file downloaded from /api/analytics/price-paths.csv")
    args = parser.parse_args(argv[1:])
    if args.last_day < args.first_day:
        parser.error("--to is before --from")
    with open(args.csv, newline="", encoding="utf-8") as source:
        sys.stdout.write(report(read_points(source), args.first_day, args.last_day, args.sessions))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
