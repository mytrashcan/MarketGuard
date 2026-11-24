#!/usr/bin/env python3
"""Check that this checkout is ready to start the pre-registered run (docs/analysis-plan.md).

Standard library only. Never prints secret values. Usage, from the repository root:
    python3 scripts/run_preflight.py            # checks .env, data files, Docker, git, host sleep
    python3 scripts/run_preflight.py --env .env --skip-docker

Exits 1 when a required check fails. Warnings do not fail the run but should be read.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

CODE = re.compile(r"\d{6}")
MINIMUM_SYMBOLS = 2000
MINIMUM_SECTOR_COVERAGE = 0.8
CONTAINER_DATA_DIR = "/data"
REQUIRED_SECRETS = ("POSTGRES_PASSWORD", "TOSS_CLIENT_ID", "TOSS_CLIENT_SECRET")


class Report:
    def __init__(self) -> None:
        self.failed = False

    def ok(self, message: str) -> None:
        print(f"  ok    {message}")

    def warn(self, message: str) -> None:
        print(f"  warn  {message}")

    def fail(self, message: str) -> None:
        self.failed = True
        print(f"  FAIL  {message}")


def read_env(path: Path) -> dict[str, str]:
    """KEY=VALUE lines; quotes around the value are removed. Comments and blank lines are ignored."""
    values = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def decode(data: bytes) -> str:
    """KRX downloads are CP949; files saved by other tools are usually UTF-8 (with or without BOM)."""
    try:
        return data.decode("utf-8-sig")
    except UnicodeDecodeError:
        return data.decode("cp949")


def split_csv(line: str) -> list[str]:
    cells, current, quoted, index = [], [], False, 0
    while index < len(line):
        char = line[index]
        if quoted and char == '"' and index + 1 < len(line) and line[index + 1] == '"':
            current.append('"')
            index += 1
        elif char == '"':
            quoted = not quoted
        elif char == "," and not quoted:
            cells.append("".join(current).strip())
            current = []
        else:
            current.append(char)
        index += 1
    cells.append("".join(current).strip())
    return cells


def symbol_codes(text: str) -> list[str]:
    """Same rule as SymbolUniverse: the first 6-digit field of each non-comment line."""
    codes = []
    for raw in text.splitlines():
        line = raw.split("#", 1)[0].strip()
        for field in line.split(","):
            cell = field.strip().replace('"', "").replace("'", "")
            if CODE.fullmatch(cell):
                codes.append(cell)
                break
    return list(dict.fromkeys(codes))


def sector_map(text: str) -> dict[str, str]:
    """Same rule as SectorDirectory: 종목코드/업종명 header columns, else the first two columns."""
    code_column, sector_column, result = 0, 1, {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        cells = split_csv(line)
        if "종목코드" in cells and "업종명" in cells:
            code_column, sector_column = cells.index("종목코드"), cells.index("업종명")
            continue
        if len(cells) > max(code_column, sector_column):
            code, sector = cells[code_column], cells[sector_column]
            if CODE.fullmatch(code) and sector:
                result[code] = sector
    return result


def host_path(env: dict[str, str], container_path: str, root: Path) -> Path | None:
    """Map a /data/... path from .env to the folder Compose mounts there."""
    if not container_path.startswith(CONTAINER_DATA_DIR + "/"):
        return None
    data_dir = Path(env.get("MARKETGUARD_DATA_DIR") or "./data")
    if not data_dir.is_absolute():
        data_dir = root / data_dir
    return data_dir / container_path[len(CONTAINER_DATA_DIR) + 1:]


def check_env(env: dict[str, str], report: Report) -> None:
    for key in REQUIRED_SECRETS:
        (report.ok if env.get(key) else report.fail)(f"{key} is {'set' if env.get(key) else 'empty'}")
    if env.get("COLLECTOR_ENABLED", "").lower() == "true":
        report.ok("COLLECTOR_ENABLED=true")
    else:
        report.fail("COLLECTOR_ENABLED must be true for the run")
    security = env.get("MARKETGUARD_SECURITY_ENABLED", "true").lower() != "false"
    if security and not (env.get("MARKETGUARD_ADMIN_USERNAME") and env.get("MARKETGUARD_ADMIN_PASSWORD")):
        report.fail("security is on but MARKETGUARD_ADMIN_USERNAME/PASSWORD are not both set")
    elif security:
        report.ok("operator login is configured")
    else:
        report.warn("security is off: keep the port on loopback only")


def check_data(env: dict[str, str], root: Path, report: Report) -> tuple[int, float]:
    symbols_setting = env.get("SCAN_SYMBOLS_FILE", "")
    sectors_setting = env.get("OUTCOME_SECTORS_FILE", "")
    codes: list[str] = []
    sectors: dict[str, str] = {}
    for key, setting in (("SCAN_SYMBOLS_FILE", symbols_setting), ("OUTCOME_SECTORS_FILE", sectors_setting)):
        path = host_path(env, setting, root)
        if path is None:
            report.fail(f"{key} must point inside {CONTAINER_DATA_DIR}/ (e.g. {CONTAINER_DATA_DIR}/symbols.csv), "
                        f"found {setting or 'nothing'}")
            continue
        if not path.is_file():
            report.fail(f"{key}: {path.relative_to(root) if path.is_relative_to(root) else path} does not exist")
            continue
        text = decode(path.read_bytes())
        if key == "SCAN_SYMBOLS_FILE":
            codes = symbol_codes(text)
        else:
            sectors = sector_map(text)
    if codes:
        (report.ok if len(codes) >= MINIMUM_SYMBOLS else report.fail)(
            f"scan universe: {len(codes)} symbols (need at least {MINIMUM_SYMBOLS} for a full-market run)")
    coverage = 0.0
    if codes and sectors:
        coverage = sum(code in sectors for code in codes) / len(codes)
        (report.ok if coverage >= MINIMUM_SECTOR_COVERAGE else report.warn)(
            f"sector mapping covers {coverage:.0%} of scanned symbols "
            f"({len(set(sectors.values()))} sectors; download both KOSPI and KOSDAQ if this is low)")
    return len(codes), coverage


def run(command: list[str], root: Path) -> subprocess.CompletedProcess:
    return subprocess.run(command, cwd=root, capture_output=True, text=True, check=False)


def check_docker(root: Path, report: Report) -> None:
    info = run(["docker", "info", "--format", "{{.ServerVersion}}"], root)
    if info.returncode != 0:
        report.fail("Docker is not running (start OrbStack or Docker Desktop)")
        return
    report.ok(f"Docker {info.stdout.strip()} is running")
    config = run(["docker", "compose", "config", "--quiet"], root)
    (report.ok if config.returncode == 0 else report.fail)(
        "compose.yaml resolves with this .env" if config.returncode == 0
        else "docker compose config failed: " + config.stderr.strip().splitlines()[-1])


def check_git(root: Path, report: Report) -> str:
    sha = run(["git", "rev-parse", "--short", "HEAD"], root).stdout.strip()
    branch = run(["git", "rev-parse", "--abbrev-ref", "HEAD"], root).stdout.strip()
    dirty = run(["git", "status", "--porcelain", "--untracked-files=no"], root).stdout.strip()
    if dirty:
        report.fail("tracked files have uncommitted changes; the run must start from a commit")
    else:
        report.ok(f"clean checkout at {sha} ({branch})")
    if branch != "main":
        report.warn("not on main: record why in the run record")
    return sha


def check_sleep(report: Report) -> None:
    if sys.platform != "darwin":
        return
    settings = run(["pmset", "-g"], Path.cwd()).stdout
    match = re.search(r"^\s*sleep\s+(\d+)(.*)$", settings, re.MULTILINE)
    if not match:
        return
    minutes, note = int(match.group(1)), match.group(2)
    if minutes == 0:
        report.ok("system sleep is disabled")
    elif "prevented" in note:
        report.warn(f"system sleeps after {minutes} min; currently {note.strip().strip('()')} "
                    "- keep it prevented during 08:50-16:40 KST on every trading day")
    else:
        report.fail(f"system sleeps after {minutes} min with nothing preventing it")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--env", default=".env", help="environment file used by docker compose")
    parser.add_argument("--skip-docker", action="store_true", help="do not check Docker or compose")
    args = parser.parse_args(argv[1:])
    root = Path(__file__).resolve().parent.parent
    env_path = (root / args.env) if not Path(args.env).is_absolute() else Path(args.env)
    report = Report()

    print("Environment")
    if not env_path.is_file():
        report.fail(f"{args.env} not found (copy .env.example)")
        env = {}
    else:
        env = read_env(env_path)
        check_env(env, report)
    print("Data files")
    symbols, coverage = check_data(env, root, report)
    print("Host")
    if not args.skip_docker:
        check_docker(root, report)
    check_sleep(report)
    print("Code")
    sha = check_git(root, report)

    print()
    print("Run record values (docs/analysis-plan.md):")
    print(f"  commit: {sha}")
    print(f"  scan universe: {symbols} symbols, sector mapping {coverage:.0%}")
    print("  detection settings: save GET /api/settings/detection after the app starts")
    print()
    print("NOT READY" if report.failed else "READY (read any warnings above)")
    return 1 if report.failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
