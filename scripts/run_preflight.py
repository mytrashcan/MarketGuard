#!/usr/bin/env python3
"""Check that this checkout is ready to start the pre-registered run (docs/analysis-plan.md).

Standard library only. Never prints setting values. Run from anywhere:
    python3 scripts/run_preflight.py

Settings are read the way `docker compose up` will see them (`docker compose config`, which applies
.env and any exported shell variables), and from .env only when Docker is unavailable.
Exits 1 when a required check fails. Warnings do not fail the run but should be read.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
from pathlib import Path

CODE = re.compile(r"\d{6}")
MINIMUM_SYMBOLS = 2000
MINIMUM_SECTOR_COVERAGE = 0.8
MINIMUM_RETENTION_MINUTES = 9 * 60  # the checklist allows the after-close check until 18:00 KST
CONTAINER_DATA_DIR = "/data"
APP_SERVICE = "app"
# Settings that must be non-empty. Labels only, so no value is ever formatted into output.
REQUIRED_VALUES = {
    "POSTGRES_PASSWORD": "database password (postgres service)",
    "TOSS_CLIENT_ID": "Toss client ID",
    "TOSS_CLIENT_SECRET": "Toss client secret",
}
BUILD_INPUTS = ("src", "build.gradle", "settings.gradle", "gradle", "gradlew", "Dockerfile", ".dockerignore")


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

    def check(self, passed: bool, message: str) -> None:
        (self.ok if passed else self.fail)(message)


def read_env_text(text: str) -> dict[str, str]:
    """KEY=VALUE lines as Compose reads them: quotes removed, ' #' starts a comment on unquoted values."""
    values = {}
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
            value = value[1:-1]
        else:
            value = value.split(" #", 1)[0].strip()
        values[key.strip()] = value
    return values


def decode(data: bytes) -> str:
    """KRX downloads are CP949; files saved by other tools are usually UTF-8, with or without a BOM."""
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


def watch_list_codes(application_yml: str) -> list[str]:
    """The toss.watch-list codes from application.yml. The collector scans these on top of the symbol file."""
    codes, inside = [], False
    for line in application_yml.splitlines():
        stripped = line.strip()
        if stripped.startswith("watch-list:"):
            inside = True
            continue
        if inside:
            match = re.match(r'-\s*["\']?(\d{6})["\']?', stripped)
            if match:
                codes.append(match.group(1))
            elif stripped and not stripped.startswith("#"):
                break
    return codes


def duration_minutes(value: str) -> float | None:
    """Spring's simple duration format as used in this project: 90m, 9h, 30s, 1d. None if unreadable."""
    match = re.fullmatch(r"\s*(\d+)\s*(ms|s|m|h|d)\s*", value or "")
    if not match:
        return None
    amount, unit = int(match.group(1)), match.group(2)
    return amount * {"ms": 1 / 60000, "s": 1 / 60, "m": 1, "h": 60, "d": 1440}[unit]


class Settings:
    """What the app container will receive: its environment and the host folder mounted at /data."""

    def __init__(self, app_env: dict[str, str], postgres_env: dict[str, str], data_dir: Path | None, source: str):
        self.app_env = app_env
        self.postgres_env = postgres_env
        self.data_dir = data_dir
        self.source = source

    @staticmethod
    def from_compose(config: dict, root: Path) -> "Settings":
        services = config.get("services", {})
        app = services.get(APP_SERVICE, {})
        data_dir = None
        for volume in app.get("volumes", []):
            if isinstance(volume, dict) and volume.get("target") == CONTAINER_DATA_DIR and volume.get("source"):
                data_dir = Path(volume["source"])
        return Settings(dict(app.get("environment") or {}), dict(services.get("postgres", {}).get("environment") or {}),
                        data_dir, "docker compose config")

    @staticmethod
    def from_env_file(values: dict[str, str], root: Path) -> "Settings":
        data_dir = Path(values.get("MARKETGUARD_DATA_DIR") or "./data")
        return Settings(values, values, data_dir if data_dir.is_absolute() else root / data_dir, ".env")

    def value(self, key: str) -> str:
        return self.app_env.get(key) or self.postgres_env.get(key) or ""


def check_settings(settings: Settings, report: Report) -> None:
    for key, label in REQUIRED_VALUES.items():
        report.check(bool(settings.value(key)), f"{label} is {'set' if settings.value(key) else 'empty'}")
    report.check(settings.value("COLLECTOR_ENABLED").lower() == "true", "collector is enabled (COLLECTOR_ENABLED=true)")
    security = settings.value("MARKETGUARD_SECURITY_ENABLED").lower() != "false"
    if security:
        report.check(bool(settings.value("MARKETGUARD_ADMIN_USERNAME") and settings.value("MARKETGUARD_ADMIN_PASSWORD")),
                     "operator login is configured")
    else:
        report.warn("security is off: keep the port on loopback only")
    minutes = duration_minutes(settings.value("SNAPSHOT_RETENTION") or "90m")
    report.check(minutes is not None and minutes >= MINIMUM_RETENTION_MINUTES,
                 "snapshot retention covers a whole session for the after-close check "
                 f"(set SNAPSHOT_RETENTION=9h)")


def check_data(settings: Settings, root: Path, report: Report) -> tuple[int, float]:
    codes: list[str] = []
    sectors: dict[str, str] = {}
    data_dir = settings.data_dir.resolve() if settings.data_dir else None
    if data_dir is None:
        report.fail(f"nothing is mounted at {CONTAINER_DATA_DIR} in the {APP_SERVICE} service")
        return 0, 0.0
    for key in ("SCAN_SYMBOLS_FILE", "OUTCOME_SECTORS_FILE"):
        setting = settings.value(key)
        if not setting.startswith(CONTAINER_DATA_DIR + "/"):
            report.fail(f"{key} must point inside {CONTAINER_DATA_DIR}/ (e.g. {CONTAINER_DATA_DIR}/symbols.csv)")
            continue
        path = (data_dir / setting[len(CONTAINER_DATA_DIR) + 1:]).resolve()
        if not path.is_relative_to(data_dir):
            report.fail(f"{key} leaves the mounted folder")
            continue
        if not path.is_file():
            report.fail(f"{key}: {path.name} is not in the mounted folder {data_dir}")
            continue
        text = decode(path.read_bytes())
        if key == "SCAN_SYMBOLS_FILE":
            codes = symbol_codes(text)
            # An empty or header-only file makes the app fall back to the 35-symbol demo list.
            report.check(len(codes) >= MINIMUM_SYMBOLS,
                         f"symbol file: {len(codes)} symbols (need at least {MINIMUM_SYMBOLS} for a full-market run)")
        else:
            sectors = sector_map(text)
    # The collector scans the symbol file plus the watch-list, so the registered universe is their union;
    # otherwise watch-list stocks missing from the file would inflate the daily coverage check.
    application_yml = root / "src" / "main" / "resources" / "application.yml"
    watch_list = watch_list_codes(application_yml.read_text(encoding="utf-8")) if application_yml.is_file() else []
    if codes:
        file_codes = set(codes)
        # The collector scans a repeated watch-list stock once, so count it once.
        extras = [code for code in dict.fromkeys(watch_list) if code not in file_codes]
        codes = codes + extras
        report.ok(f"scan universe: {len(codes)} symbols (symbol file plus {len(extras)} watch-list stocks not in it)"
                  " - record this number")
    coverage = 0.0
    if codes and sectors:
        coverage = sum(code in sectors for code in codes) / len(codes)
        (report.ok if coverage >= MINIMUM_SECTOR_COVERAGE else report.warn)(
            f"sector mapping covers {coverage:.0%} of scanned symbols "
            f"({len(set(sectors.values()))} sectors; download both KOSPI and KOSDAQ if this is low)")
    return len(codes), coverage


def run(command: list[str], root: Path) -> subprocess.CompletedProcess | None:
    try:
        return subprocess.run(command, cwd=root, capture_output=True, text=True, check=False)
    except FileNotFoundError:
        return None


def load_settings(root: Path, report: Report) -> Settings | None:
    info = run(["docker", "info", "--format", "{{.ServerVersion}}"], root)
    if info is None:
        report.fail("Docker CLI not found (install OrbStack or Docker Desktop)")
    elif info.returncode != 0:
        report.fail("Docker is not running (start OrbStack or Docker Desktop)")
    else:
        report.ok(f"Docker {info.stdout.strip()} is running")
        config = run(["docker", "compose", "config", "--format", "json"], root)
        if config is not None and config.returncode == 0:
            report.ok("compose.yaml resolves; checking the settings Compose will pass to the app")
            return Settings.from_compose(json.loads(config.stdout), root)
        report.fail("docker compose config failed (check .env)")
    env_file = root / ".env"
    if not env_file.is_file():
        report.fail(".env not found (copy .env.example)")
        return None
    report.warn("checking .env directly; exported shell variables could still change what Compose uses")
    return Settings.from_env_file(read_env_text(env_file.read_text(encoding="utf-8")), root)


def check_git(root: Path, report: Report) -> str:
    sha = (run(["git", "rev-parse", "--short", "HEAD"], root) or subprocess.CompletedProcess([], 1, "", "")).stdout.strip()
    branch = (run(["git", "rev-parse", "--abbrev-ref", "HEAD"], root) or subprocess.CompletedProcess([], 1, "", "")).stdout.strip()
    tracked = run(["git", "status", "--porcelain", "--untracked-files=no"], root)
    # The image is built from the working tree, so untracked files in build inputs would ship without
    # being in the recorded commit.
    build_inputs = run(["git", "status", "--porcelain", "--untracked-files=all", "--", *BUILD_INPUTS], root)
    report.check(tracked is not None and not tracked.stdout.strip(),
                 f"no uncommitted changes to tracked files at {sha} ({branch})")
    report.check(build_inputs is not None and not build_inputs.stdout.strip(),
                 "no untracked files in the image build inputs (" + ", ".join(BUILD_INPUTS) + ")")
    if branch != "main":
        report.warn("not on main: record why in the run record")
    return sha


def check_sleep(root: Path, report: Report) -> None:
    if sys.platform != "darwin":
        return
    result = run(["pmset", "-g"], root)
    match = re.search(r"^\s*sleep\s+(\d+)(.*)$", result.stdout if result else "", re.MULTILINE)
    if not match:
        return
    minutes, note = int(match.group(1)), match.group(2).strip().strip("()")
    if minutes == 0:
        report.ok("system sleep is disabled")
    elif "prevented" in note:
        report.warn(f"system sleeps after {minutes} min; currently {note} "
                    "- keep it prevented during 08:50-16:40 KST on every trading day")
    else:
        report.fail(f"system sleeps after {minutes} min with nothing preventing it")


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    report = Report()
    print("Docker and settings")
    settings = load_settings(root, report)
    if settings is not None:
        check_settings(settings, report)
    print("Data files")
    symbols, coverage = check_data(settings, root, report) if settings else (0, 0.0)
    print("Host")
    check_sleep(root, report)
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
    sys.exit(main())
