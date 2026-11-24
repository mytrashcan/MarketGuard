"""Tests for analyze_price_paths.py. Run: python3 -m unittest discover -s scripts -p 'test_*.py'"""

import io
import math
import unittest
from decimal import Decimal

import analyze_price_paths as analysis

HEADER = ("anomaly_id,detected_at,stock_code,rule_type,severity,direction,observed_value,threshold_value,"
          "deviation_ratio,offset_minutes,base_price,price,change_percent,market_change_percent,"
          "sector_change_percent\n")


def line(anomaly_id, detected_at, stock, rule, direction, offset, change, market="", sector=""):
    return (f"{anomaly_id},{detected_at},{stock},{rule},WARNING,{direction},4,3,1.3,{offset},100,"
            f"{'' if change == '' else 100 + float(change)},{change},{market},{sector}\n")


def points(*lines):
    return analysis.read_points(io.StringIO(HEADER + "".join(lines)))


class StatisticsTest(unittest.TestCase):

    def test_exact_binomial_matches_known_values(self):
        # 10 trials, 9 successes: two-sided p = 2 * (C(10,9) + C(10,10)) / 1024
        self.assertAlmostEqual(analysis.binomial_two_sided_p(9, 10), 22 / 1024)
        self.assertAlmostEqual(analysis.binomial_two_sided_p(5, 10), 1.0)

    def test_wilson_interval_matches_reference(self):
        low, high = analysis.wilson(60, 100)
        self.assertAlmostEqual(low, 0.5020, places=3)
        self.assertAlmostEqual(high, 0.6906, places=3)

    def test_holm_is_monotone_and_capped(self):
        adjusted = analysis.holm({"a": 0.01, "b": 0.04, "c": 0.03, "d": 0.5})
        self.assertAlmostEqual(adjusted["a"], 0.04)
        self.assertAlmostEqual(adjusted["c"], 0.09)
        self.assertAlmostEqual(adjusted["b"], 0.09)
        self.assertAlmostEqual(adjusted["d"], 0.5)

    def test_verdicts_follow_the_plan(self):
        self.assertEqual(analysis.verdict(0.70, 99, 0.0001), "insufficient")
        self.assertEqual(analysis.verdict(0.60, 200, 0.001), "continuation")
        self.assertEqual(analysis.verdict(0.40, 200, 0.001), "reversal")
        self.assertEqual(analysis.verdict(0.53, 5000, 0.001), "no evidence (significant, below practical threshold)")
        self.assertEqual(analysis.verdict(0.60, 200, 0.2), "no evidence")


class SelectionTest(unittest.TestCase):

    def test_keeps_only_the_first_signal_per_stock_rule_and_kst_day(self):
        data = points(
            # 2026-07-31 09:10 KST and 09:40 KST: same stock-day, the later one is dropped
            line(2, "2026-07-31T00:40:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"),
            line(1, "2026-07-31T00:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, "-1", "0"),
            # 2026-07-30 23:50 UTC is already 2026-07-31 08:50 KST, but another rule: kept
            line(3, "2026-07-30T23:50:00Z", "005930", "VOLUME_SURGE", "UP", 30, "1", "0"),
            # next KST day: kept
            line(4, "2026-07-31T15:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"))

        self.assertEqual(sorted(analysis.first_signal_per_stock_day(data)), [1, 3, 4])

    def test_does_not_replace_an_unusable_first_signal(self):
        data = points(
            line(1, "2026-07-31T00:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, ""),
            line(2, "2026-07-31T00:40:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"))
        signals = analysis.first_signal_per_stock_day(data)

        self.assertEqual(analysis.outcomes(signals, "PRICE_SPIKE", 30, "market"), [])


class OutcomeTest(unittest.TestCase):

    def test_market_adjustment_decides_the_direction(self):
        data = points(
            # +1% while the market rose 2%: underperformed, so an UP signal did not follow
            line(1, "2026-07-31T00:10:00Z", "000001", "PRICE_SPIKE", "UP", 30, "1", "2"),
            # SELL pressure and the stock lagged the market: followed
            line(2, "2026-07-31T00:10:00Z", "000002", "ORDERBOOK_IMBALANCE", "SELL", 30, "0.5", "1"),
            # no market benchmark: excluded from the market-adjusted metric
            line(3, "2026-07-31T00:10:00Z", "000003", "PRICE_SPIKE", "UP", 30, "1", ""),
            # exact tie: excluded
            line(4, "2026-07-31T00:10:00Z", "000004", "PRICE_SPIKE", "DOWN", 30, "1", "1"))
        signals = analysis.first_signal_per_stock_day(data)

        self.assertEqual(analysis.outcomes(signals, "PRICE_SPIKE", 30, "market"), [False])
        self.assertEqual(analysis.outcomes(signals, "PRICE_SPIKE", 30, "raw"), [True, True, False])
        self.assertEqual(analysis.outcomes(signals, "ORDERBOOK_IMBALANCE", 30, "market"), [True])

    def test_report_marks_small_samples_insufficient(self):
        data = points(line(1, "2026-07-31T00:10:00Z", "000001", "PRICE_SPIKE", "UP", 30, "1", "0"))

        report = analysis.report(data)

        self.assertIn("| `PRICE_SPIKE` | 1 | 1 | 100.0% |", report)
        self.assertIn("insufficient", report)
        self.assertIn("## Exploratory", report)

    def test_formula_prefixed_stock_codes_are_read_back(self):
        data = points(line(1, "2026-07-31T00:10:00Z", "'=X", "PRICE_SPIKE", "UP", 30, "1", "0"))
        self.assertEqual(data[0].stock_code, "=X")
        self.assertEqual(data[0].change, Decimal("1"))
        self.assertTrue(math.isnan(analysis.wilson(0, 0)[0]))


if __name__ == "__main__":
    unittest.main()
