#!/usr/bin/env python3
"""Analyze /api/analytics/price-paths.csv exactly as docs/analysis-plan.md specifies.

Standard library only. Usage:
    python3 scripts/analyze_price_paths.py price-paths.csv > results.md

The confirmatory section implements the pre-registered primary analysis. Everything under
"Exploratory" is descriptive and must not be used to claim that a rule works.
"""

from __future__ import annotations

import csv
import math
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation

KST = timezone(timedelta(hours=9))

# Pre-registered parameters (docs/analysis-plan.md). Changing any of these is a plan amendment.
CONFIRMATORY_RULES = ("PRICE_SPIKE", "PRICE_LIMIT", "ORDERBOOK_IMBALANCE", "VOLUME_SURGE", "PRICE_VOLUME_SURGE")
PRIMARY_OFFSET = 30
ALPHA = 0.05
MINIMUM_SAMPLES = 100
PRACTICAL_RATE = 0.55
UPWARD = {"UP", "BUY"}
DOWNWARD = {"DOWN", "SELL"}
EXPLORATORY_OFFSETS = (5, 15, 30, 60)


@dataclass(frozen=True)
class Point:
    anomaly_id: int
    detected_at: datetime
    stock_code: str
    rule_type: str
    direction: str
    offset: int
    change: Decimal | None
    market: Decimal | None
    sector: Decimal | None


def _decimal(value: str) -> Decimal | None:
    if value == "":
        return None
    try:
        return Decimal(value)
    except InvalidOperation as error:
        raise ValueError(f"not a number: {value!r}") from error


def read_points(lines) -> list[Point]:
    points = []
    for row in csv.DictReader(lines):
        points.append(Point(
            anomaly_id=int(row["anomaly_id"]),
            detected_at=datetime.fromisoformat(row["detected_at"].replace("Z", "+00:00")),
            stock_code=row["stock_code"].lstrip("'"),
            rule_type=row["rule_type"],
            direction=row["direction"],
            offset=int(row["offset_minutes"]),
            change=_decimal(row["change_percent"]),
            market=_decimal(row["market_change_percent"]),
            sector=_decimal(row["sector_change_percent"]),
        ))
    return points


def first_signal_per_stock_day(points: list[Point]) -> dict[int, list[Point]]:
    """One signal per (stock, rule, KST day): the earliest, ties broken by the lowest id.

    The choice ignores outcomes, so a first signal without a usable value drops the stock-day
    instead of being replaced by a later signal that happens to have one.
    """
    by_signal: dict[int, list[Point]] = {}
    for point in points:
        by_signal.setdefault(point.anomaly_id, []).append(point)
    first: dict[tuple[str, str, object], tuple[datetime, int]] = {}
    for anomaly_id, signal_points in by_signal.items():
        sample = signal_points[0]
        key = (sample.stock_code, sample.rule_type, sample.detected_at.astimezone(KST).date())
        candidate = (sample.detected_at, anomaly_id)
        if key not in first or candidate < first[key]:
            first[key] = candidate
    return {anomaly_id: by_signal[anomaly_id] for _, anomaly_id in first.values()}


def followed(direction: str, move: Decimal) -> bool | None:
    """Whether a move went the signal's way; None for no direction or an exact zero (a tie)."""
    if move == 0 or (direction not in UPWARD and direction not in DOWNWARD):
        return None
    return (move > 0) == (direction in UPWARD)


def adjusted_move(point: Point, adjustment: str) -> Decimal | None:
    if point.change is None:
        return None
    if adjustment == "raw":
        return point.change
    benchmark = point.market if adjustment == "market" else point.sector
    return None if benchmark is None else point.change - benchmark


def outcomes(signals: dict[int, list[Point]], rule: str, offset: int, adjustment: str) -> list[bool]:
    results = []
    for signal_points in signals.values():
        if signal_points[0].rule_type != rule:
            continue
        point = next((p for p in signal_points if p.offset == offset), None)
        if point is None:
            continue
        move = adjusted_move(point, adjustment)
        result = None if move is None else followed(point.direction, move)
        if result is not None:
            results.append(result)
    return results


def wilson(successes: int, n: int, z: float = 1.959963984540054) -> tuple[float, float]:
    if n == 0:
        return (math.nan, math.nan)
    p = successes / n
    denominator = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denominator
    margin = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denominator
    return (max(0.0, centre - margin), min(1.0, centre + margin))


def binomial_two_sided_p(successes: int, n: int) -> float:
    """Exact two-sided test against 50%: total probability of outcomes no more likely than the observed one."""
    if n == 0:
        return 1.0

    def log_pmf(k: int) -> float:
        return math.lgamma(n + 1) - math.lgamma(k + 1) - math.lgamma(n - k + 1) - n * math.log(2)

    observed = log_pmf(successes)
    total = sum(math.exp(log_pmf(k)) for k in range(n + 1) if log_pmf(k) <= observed + 1e-7)
    return min(1.0, total)


def holm(p_values: dict[str, float]) -> dict[str, float]:
    ordered = sorted(p_values.items(), key=lambda item: item[1])
    adjusted, running = {}, 0.0
    for index, (name, p) in enumerate(ordered):
        running = max(running, min(1.0, (len(ordered) - index) * p))
        adjusted[name] = running
    return adjusted


def verdict(rate: float, n: int, adjusted_p: float | None) -> str:
    if n < MINIMUM_SAMPLES:
        return "insufficient"
    if adjusted_p is not None and adjusted_p < ALPHA:
        if rate >= PRACTICAL_RATE:
            return "continuation"
        if rate <= 1 - PRACTICAL_RATE:
            return "reversal"
        return "no evidence (significant, below practical threshold)"
    return "no evidence"


def confirmatory(signals: dict[int, list[Point]]) -> list[dict]:
    rows = []
    for rule in CONFIRMATORY_RULES:
        results = outcomes(signals, rule, PRIMARY_OFFSET, "market")
        n, k = len(results), sum(results)
        rows.append({"rule": rule, "n": n, "k": k, "rate": k / n if n else math.nan,
                     "ci": wilson(k, n), "p": binomial_two_sided_p(k, n) if n >= MINIMUM_SAMPLES else None})
    adjusted = holm({row["rule"]: row["p"] for row in rows if row["p"] is not None})
    for row in rows:
        row["p_holm"] = adjusted.get(row["rule"])
        row["verdict"] = verdict(row["rate"], row["n"], row["p_holm"])
    return rows


def _pct(value: float) -> str:
    return "–" if math.isnan(value) else f"{value * 100:.1f}%"


def _p(value: float | None) -> str:
    return "–" if value is None else f"{value:.4f}"


def report(points: list[Point]) -> str:
    signals = first_signal_per_stock_day(points)
    days = sorted({p.detected_at.astimezone(KST).date() for p in points})
    lines = [
        "# MarketGuard price path analysis",
        "",
        f"- Input: {len(points)} points, {len({p.anomaly_id for p in points})} signals, "
        f"{len(signals)} stock-days, {len(days)} KST days"
        + (f" ({days[0]} – {days[-1]})" if days else ""),
        f"- Plan: docs/analysis-plan.md (primary offset {PRIMARY_OFFSET} min, market-adjusted, "
        f"Holm α={ALPHA}, n ≥ {MINIMUM_SAMPLES}, practical rate {PRACTICAL_RATE:.0%})",
        "",
        "## Confirmatory",
        "",
        "| Rule | Stock-days | Followed | Rate | 95% CI | p | p (Holm) | Verdict |",
        "|---|---:|---:|---:|---|---:|---:|---|",
    ]
    for row in confirmatory(signals):
        low, high = row["ci"]
        lines.append(f"| `{row['rule']}` | {row['n']} | {row['k']} | {_pct(row['rate'])} | "
                     f"{_pct(low)} – {_pct(high)} | {_p(row['p'])} | {_p(row['p_holm'])} | {row['verdict']} |")
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
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    with open(argv[1], newline="", encoding="utf-8") as source:
        sys.stdout.write(report(read_points(source)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
