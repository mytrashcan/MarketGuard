"""Tests for analyze_price_paths.py. Run: python3 -m unittest discover -s scripts -p 'test_*.py'"""

import io
import math
import unittest
from datetime import date

import analyze_price_paths as analysis

HEADER = ("anomaly_id,detected_at,stock_code,rule_type,severity,direction,observed_value,threshold_value,"
          "deviation_ratio,offset_minutes,base_price,price,change_percent,market_change_percent,"
          "sector_change_percent\n")
RUN = (date(2026, 7, 1), date(2026, 8, 31))


def line(anomaly_id, detected_at, stock, rule, direction, offset, change, market="", sector=""):
    price = "" if change == "" else 100 + float(change)
    return (f"{anomaly_id},{detected_at},{stock},{rule},WARNING,{direction},4,3,1.3,{offset},100,"
            f"{price},{change},{market},{sector}\n")


def unrecorded(anomaly_id, detected_at, stock, rule, direction):
    """A signal exported without any price point (the recorder missed it)."""
    return f"{anomaly_id},{detected_at},{stock},{rule},WARNING,{direction},4,3,1.3,,,,,,\n"


def points(*lines):
    return analysis.read_points(io.StringIO(HEADER + "".join(lines)))


class ReadingTest(unittest.TestCase):

    def test_rejects_a_download_without_the_export_header(self):
        for body in ("", '{"code":"AUTHENTICATION_REQUIRED"}\n'):
            with self.assertRaisesRegex(ValueError, "missing columns"):
                analysis.read_points(io.StringIO(body))

    def test_reads_signals_without_points_and_formula_prefixed_codes(self):
        data = points(unrecorded(1, "2026-07-31T00:10:00Z", "'=X", "PRICE_SPIKE", "UP"))
        self.assertIsNone(data[0].offset)
        self.assertIsNone(data[0].change)
        self.assertEqual(data[0].stock_code, "=X")

    def test_keeps_only_rows_inside_the_registered_kst_dates(self):
        data = points(
            line(1, "2026-07-31T14:59:00Z", "000001", "PRICE_SPIKE", "UP", 30, "1", "0"),  # 07-31 23:59 KST
            line(2, "2026-07-31T15:00:00Z", "000001", "PRICE_SPIKE", "UP", 30, "1", "0"))  # 08-01 00:00 KST
        self.assertEqual([p.anomaly_id for p in analysis.within_run(data, date(2026, 7, 1), date(2026, 7, 31))],
                         [1])


class StatisticsTest(unittest.TestCase):

    def test_sign_flip_with_one_signal_per_day_equals_the_exact_binomial(self):
        # 9 of 10 single-signal days followed: two-sided p = 2 * (C(10,9) + C(10,10)) / 1024
        days = [(1, 1)] * 9 + [(0, 1)]
        self.assertAlmostEqual(analysis.day_sign_flip_p(days), 22 / 1024)
        self.assertAlmostEqual(analysis.day_sign_flip_p([(1, 1), (0, 1)]), 1.0)

    def test_sign_flip_treats_a_day_moving_together_as_one_observation(self):
        # 40 of 40 on a single day: as independent coins p would be ~2e-12; as one day it cannot be rare.
        self.assertAlmostEqual(analysis.day_sign_flip_p([(40, 40)]), 1.0)
        # Three days that each lean the same way: 2 of 8 sign assignments are as extreme
        self.assertAlmostEqual(analysis.day_sign_flip_p([(5, 5), (3, 4), (2, 3)]), 0.25)

    def test_student_t_tail_matches_tables(self):
        self.assertAlmostEqual(analysis.student_t_two_sided_p(2.086, 20), 0.05, places=3)
        self.assertAlmostEqual(analysis.student_t_two_sided_p(2.228, 10), 0.05, places=3)
        self.assertAlmostEqual(analysis.student_t_two_sided_p(0.0, 15), 1.0)

    def test_clustered_t_tests_the_pooled_rate_without_assuming_symmetric_days(self):
        # Same pooled rate (60%), but days that disagree widely give a much weaker result than steady days
        steady = [(6, 10)] * 20
        noisy = [(10, 10), (2, 10)] * 10
        self.assertEqual(analysis.day_clustered_t_p(steady), 1.0)  # no day-to-day variation: untestable
        self.assertLess(analysis.day_clustered_t_p([(6, 10), (7, 10), (5, 10), (6, 10)] * 5), 0.001)
        self.assertGreater(analysis.day_clustered_t_p(noisy), 0.05)
        self.assertEqual(analysis.day_clustered_t_p([(4, 10)]), 1.0)

    def test_bootstrap_interval_is_reproducible_and_brackets_the_rate(self):
        days = [(6, 10), (5, 10), (7, 10), (4, 10), (6, 10)] * 4
        low, high = analysis.day_bootstrap_interval(days)
        self.assertEqual((low, high), analysis.day_bootstrap_interval(days))
        self.assertLess(low, 0.56)
        self.assertGreater(high, 0.56)
        self.assertTrue(math.isnan(analysis.day_bootstrap_interval([])[0]))

    def test_holm_is_monotone_and_capped(self):
        adjusted = analysis.holm({"a": 0.01, "b": 0.04, "c": 0.03, "d": 0.5})
        self.assertAlmostEqual(adjusted["a"], 0.04)
        self.assertAlmostEqual(adjusted["c"], 0.09)
        self.assertAlmostEqual(adjusted["b"], 0.09)
        self.assertAlmostEqual(adjusted["d"], 0.5)

    def test_verdicts_follow_the_plan_with_inclusive_boundaries(self):
        self.assertEqual(analysis.verdict(70, 99, 20, 0.0001), "insufficient")
        self.assertEqual(analysis.verdict(700, 1000, 9, 0.0001), "insufficient")
        self.assertEqual(analysis.verdict(550, 1000, 20, 0.001), "continuation")
        self.assertEqual(analysis.verdict(450, 1000, 20, 0.001), "reversal")
        self.assertEqual(analysis.verdict(549, 1000, 20, 0.001), "no evidence (significant, below practical threshold)")
        self.assertEqual(analysis.verdict(451, 1000, 20, 0.001), "no evidence (significant, below practical threshold)")
        self.assertEqual(analysis.verdict(600, 1000, 20, 0.2), "no evidence")
        # both day-level tests must pass
        self.assertEqual(analysis.verdict(600, 1000, 20, 0.001, 0.2), "no evidence")
        self.assertEqual(analysis.verdict(600, 1000, 20, 0.001, 0.001), "continuation")
        # a run shorter than the registered 20 sessions never gets a confirmatory verdict
        self.assertEqual(analysis.verdict(600, 1000, 20, 0.001, 0.001, sessions=10),
                         "insufficient (run shorter than 20 sessions)")


class SelectionTest(unittest.TestCase):

    def test_keeps_only_the_first_signal_per_stock_rule_and_kst_day(self):
        data = points(
            # 09:10 and 09:40 KST: same stock-day, the later one is dropped
            line(2, "2026-07-31T00:40:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"),
            line(1, "2026-07-31T00:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, "-1", "0"),
            # another rule on the same stock-day: kept
            line(3, "2026-07-30T23:50:00Z", "005930", "VOLUME_SURGE", "UP", 30, "1", "0"),
            # next KST day: kept
            line(4, "2026-07-31T15:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"))

        self.assertEqual(sorted(analysis.first_signal_per_stock_day(data)), [1, 3, 4])

    def test_does_not_replace_a_first_signal_without_a_usable_value(self):
        for first in (line(1, "2026-07-31T00:10:00Z", "005930", "PRICE_SPIKE", "UP", 30, ""),
                      unrecorded(1, "2026-07-31T00:10:00Z", "005930", "PRICE_SPIKE", "UP")):
            data = points(first, line(2, "2026-07-31T00:40:00Z", "005930", "PRICE_SPIKE", "UP", 30, "1", "0"))
            signals = analysis.first_signal_per_stock_day(data)
            self.assertEqual(list(signals), [1])
            self.assertEqual(analysis.outcomes(signals, "PRICE_SPIKE", 30, "market"), [])

    def test_signals_without_a_direction_never_take_the_first_slot(self):
        data = points(
            line(1, "2026-07-31T00:10:00Z", "005930", "VOLUME_SURGE", "NONE", 30, "0", "0"),
            line(2, "2026-07-31T00:40:00Z", "005930", "VOLUME_SURGE", "UP", 30, "1", "0"))

        self.assertEqual(list(analysis.first_signal_per_stock_day(data)), [2])


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
        self.assertEqual(sorted(analysis.outcomes(signals, "PRICE_SPIKE", 30, "raw")), [False, True, True])
        self.assertEqual(analysis.outcomes(signals, "ORDERBOOK_IMBALANCE", 30, "market"), [True])

    def test_report_ignores_rows_outside_the_run_and_marks_small_samples(self):
        data = points(
            line(1, "2026-07-31T00:10:00Z", "000001", "PRICE_SPIKE", "UP", 30, "1", "0"),
            line(2, "2026-06-01T00:10:00Z", "000002", "PRICE_SPIKE", "UP", 30, "1", "0"))

        report = analysis.report(data, *RUN)

        self.assertIn("Rows outside it ignored: 1", report)
        self.assertIn("| `PRICE_SPIKE` | 1 | 1 | 1 | 100.0% |", report)
        self.assertIn("insufficient", report)
        self.assertIn("## Exploratory", report)


if __name__ == "__main__":
    unittest.main()
