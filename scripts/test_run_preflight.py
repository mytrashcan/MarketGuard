"""Tests for run_preflight.py. Run: python3 -m unittest discover -s scripts -p 'test_*.py'"""

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


class ParsingTest(unittest.TestCase):

    def test_takes_the_short_code_not_the_standard_code(self):
        self.assertEqual(preflight.symbol_codes(KRX_SYMBOLS), ["005930", "000660"])

    def test_reads_concatenated_sector_files_with_quoted_numbers(self):
        self.assertEqual(preflight.sector_map(KRX_SECTORS), {"005930": "전기·전자", "247540": "일반전기전자"})

    def test_decodes_cp949_and_utf8_with_bom(self):
        self.assertEqual(preflight.decode("업종명".encode("cp949")), "업종명")
        self.assertEqual(preflight.decode("﻿업종명".encode("utf-8")), "업종명")

    def test_reads_env_values_without_quotes_and_ignores_comments(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / ".env"
            path.write_text("# comment\nA=1\nB='two words'\nC=\"x=y\"\nEMPTY=\n", encoding="utf-8")
            self.assertEqual(preflight.read_env(path), {"A": "1", "B": "two words", "C": "x=y", "EMPTY": ""})


class DataCheckTest(unittest.TestCase):

    def test_maps_container_paths_to_the_mounted_folder_and_reports_coverage(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "data").mkdir()
            (root / "data" / "symbols.csv").write_text(KRX_SYMBOLS, encoding="utf-8")
            (root / "data" / "sectors.csv").write_bytes(KRX_SECTORS.encode("cp949"))
            env = {"SCAN_SYMBOLS_FILE": "/data/symbols.csv", "OUTCOME_SECTORS_FILE": "/data/sectors.csv"}
            report = preflight.Report()

            symbols, coverage = preflight.check_data(env, root, report)

            self.assertEqual(symbols, 2)
            self.assertEqual(coverage, 0.5)
            self.assertTrue(report.failed)  # 2 symbols is far below a full-market universe

    def test_rejects_paths_outside_the_mounted_folder(self):
        report = preflight.Report()
        preflight.check_data({"SCAN_SYMBOLS_FILE": "/Users/me/symbols.csv"}, Path("."), report)
        self.assertTrue(report.failed)

    def test_requires_secrets_and_the_collector(self):
        report = preflight.Report()
        preflight.check_env({"POSTGRES_PASSWORD": "x", "TOSS_CLIENT_ID": "x", "TOSS_CLIENT_SECRET": "x",
                             "COLLECTOR_ENABLED": "true", "MARKETGUARD_ADMIN_USERNAME": "u",
                             "MARKETGUARD_ADMIN_PASSWORD": "p"}, report)
        self.assertFalse(report.failed)
        report = preflight.Report()
        preflight.check_env({"POSTGRES_PASSWORD": "x", "TOSS_CLIENT_ID": "", "COLLECTOR_ENABLED": "false"}, report)
        self.assertTrue(report.failed)


if __name__ == "__main__":
    unittest.main()
