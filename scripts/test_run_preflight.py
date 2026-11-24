"""Tests for run_preflight.py. Run: python3 -m unittest discover -s scripts -p 'test_*.py'"""

import contextlib
import io
import tempfile
import unittest
from pathlib import Path

import run_preflight as preflight

# Shape of the KRX 전종목 기본정보 download: 12-character standard code first, 6-digit short code second.
KRX_SYMBOLS = (
    "표준코드,단축코드,한글 종목명,한글 종목약명,영문 종목명,상장일,시장구분\n"
    "KR7005930003,005930,삼성전자보통주,삼성전자,SamsungElectronics,1975/06/11,KOSPI\n"
    '"KR7000660001","000660","SK하이닉스보통주","SK하이닉스","SK hynix","1996/12/26","KOSPI"\n'
)
# Shape of the KRX 업종분류 현황 download, KOSPI and KOSDAQ files concatenated.
KRX_SECTORS = (
    "종목코드,종목명,시장구분,업종명,종가\n"
    '005930,삼성전자,KOSPI,전기·전자,"71,000"\n'
    "종목코드,종목명,시장구분,업종명,종가\n"
    "247540,에코프로비엠,KOSDAQ,일반전기전자,200000\n"
)
COMPLETE = {"POSTGRES_PASSWORD": "x", "TOSS_CLIENT_ID": "x", "TOSS_CLIENT_SECRET": "x", "COLLECTOR_ENABLED": "true",
            "MARKETGUARD_ADMIN_USERNAME": "u", "MARKETGUARD_ADMIN_PASSWORD": "p", "SNAPSHOT_RETENTION": "9h",
            "SCAN_SYMBOLS_FILE": "/data/symbols.csv", "OUTCOME_SECTORS_FILE": "/data/sectors.csv"}


def quietly(function, *args):
    report = preflight.Report()
    with contextlib.redirect_stdout(io.StringIO()):
        result = function(*args, report)
    return report, result


class ParsingTest(unittest.TestCase):

    def test_takes_the_short_code_not_the_standard_code(self):
        self.assertEqual(preflight.symbol_codes(KRX_SYMBOLS), ["005930", "000660"])

    def test_reads_concatenated_sector_files_with_quoted_numbers(self):
        self.assertEqual(preflight.sector_map(KRX_SECTORS), {"005930": "전기·전자", "247540": "일반전기전자"})

    def test_reads_the_header_after_a_utf8_byte_order_mark(self):
        text = preflight.decode("﻿종목코드,종목명,시장구분,업종명\n005930,삼성전자,KOSPI,전기·전자\n".encode("utf-8"))
        self.assertEqual(preflight.sector_map(text), {"005930": "전기·전자"})
        self.assertEqual(preflight.decode("업종명".encode("cp949")), "업종명")

    def test_reads_env_like_compose_with_quotes_and_inline_comments(self):
        values = preflight.read_env_text("# comment\nA=1\nB='two # words'\nC=\"x=y\"\nD=marketguard # local\nE=\n")
        self.assertEqual(values, {"A": "1", "B": "two # words", "C": "x=y", "D": "marketguard", "E": ""})

    def test_reads_the_watch_list_block_only(self):
        yml = ('toss:\n  watch-list:          # focus\n    - "005930"   # 삼성전자\n    - 000660\n'
               "\n# next section\ncollector:\n  enabled: false\n  - \"111111\"\n")
        self.assertEqual(preflight.watch_list_codes(yml), ["005930", "000660"])

    def test_reads_spring_durations(self):
        self.assertEqual(preflight.duration_minutes("9h"), 540)
        self.assertEqual(preflight.duration_minutes("90m"), 90)
        self.assertIsNone(preflight.duration_minutes("PT9H"))


class SettingsTest(unittest.TestCase):

    def test_uses_the_environment_and_mount_that_compose_resolves(self):
        config = {"services": {
            "app": {"environment": {"COLLECTOR_ENABLED": "false"},
                    "volumes": [{"type": "bind", "source": "/srv/krx", "target": "/data", "read_only": True}]},
            "postgres": {"environment": {"POSTGRES_PASSWORD": "x"}}}}
        settings = preflight.Settings.from_compose(config, Path("."))
        self.assertEqual(settings.value("COLLECTOR_ENABLED"), "false")
        self.assertEqual(settings.value("POSTGRES_PASSWORD"), "x")
        self.assertEqual(settings.data_dir, Path("/srv/krx"))
        report, _ = quietly(preflight.check_settings, settings)
        self.assertTrue(report.failed)

    def test_accepts_a_complete_configuration_and_requires_long_retention(self):
        report, _ = quietly(preflight.check_settings, preflight.Settings(COMPLETE, {}, None, "test"))
        self.assertFalse(report.failed)
        for too_short in ("90m", "8h"):  # the checklist allows the check until 18:00, nine hours after the open
            report, _ = quietly(preflight.check_settings,
                                preflight.Settings({**COMPLETE, "SNAPSHOT_RETENTION": too_short}, {}, None, "test"))
            self.assertTrue(report.failed)

    def test_never_prints_setting_values(self):
        secret = "s3cr3t-value"
        settings = preflight.Settings({**COMPLETE, "TOSS_CLIENT_SECRET": secret, "TOSS_CLIENT_ID": ""}, {}, None, "t")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            preflight.check_settings(settings, preflight.Report())
        self.assertNotIn(secret, output.getvalue())


class DataCheckTest(unittest.TestCase):

    def write(self, root: Path, symbols: str, sectors: str) -> preflight.Settings:
        (root / "data").mkdir()
        (root / "data" / "symbols.csv").write_text(symbols, encoding="utf-8")
        (root / "data" / "sectors.csv").write_bytes(sectors.encode("cp949"))
        return preflight.Settings(COMPLETE, {}, root / "data", "test")

    def test_reports_universe_size_and_sector_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            settings = self.write(Path(directory), KRX_SYMBOLS, KRX_SECTORS)
            report, (symbols, coverage) = quietly(preflight.check_data, settings, Path(directory))
            self.assertEqual((symbols, coverage), (2, 0.5))
            self.assertTrue(report.failed)  # 2 symbols is far below a full-market universe

    def test_registered_universe_includes_watch_list_stocks_missing_from_the_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            settings = self.write(root, KRX_SYMBOLS, KRX_SECTORS)
            (root / "src" / "main" / "resources").mkdir(parents=True)
            (root / "src" / "main" / "resources" / "application.yml").write_text(
                'toss:\n  watch-list:\n    - "005930"\n    - "035720"\n    - "035720"\n', encoding="utf-8")
            _, (symbols, _) = quietly(preflight.check_data, settings, root)
            self.assertEqual(symbols, 3)  # 005930, 000660 from the file plus 035720 once, though listed twice

    def test_fails_a_header_only_symbol_file(self):
        with tempfile.TemporaryDirectory() as directory:
            settings = self.write(Path(directory), "표준코드,단축코드,한글 종목명\n", KRX_SECTORS)
            report, (symbols, _) = quietly(preflight.check_data, settings, Path(directory))
            self.assertEqual(symbols, 0)
            self.assertTrue(report.failed)

    def test_rejects_paths_outside_the_mounted_folder(self):
        with tempfile.TemporaryDirectory() as directory:
            settings = self.write(Path(directory), KRX_SYMBOLS, KRX_SECTORS)
            settings.app_env = {**COMPLETE, "SCAN_SYMBOLS_FILE": "/data/../../etc/passwd",
                                "OUTCOME_SECTORS_FILE": "/elsewhere/sectors.csv"}
            report, (symbols, _) = quietly(preflight.check_data, settings, Path(directory))
            self.assertTrue(report.failed)
            self.assertEqual(symbols, 0)


if __name__ == "__main__":
    unittest.main()
