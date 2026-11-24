# Operations runbook

## Production prerequisites

- TLS-terminating reverse proxy and network ACL
- Docker/Compose or an equivalent orchestrator
- PostgreSQL 16-compatible service
- Long random database and operator passwords
- Optional Toss client ID/secret and registered outbound IP when collection is enabled
- Exactly one app replica for each Toss client credential

Start from `.env.example`, store real values in a secret manager, and never commit the resulting file.

```bash
docker compose config --quiet
docker compose up --build --detach --wait
docker compose ps
```

The app binds the host port to `127.0.0.1`; publish it through an authenticated TLS reverse proxy. Set `MARKETGUARD_ALLOWED_ORIGINS` to the exact public HTTPS origin.

For a single-user machine that remains bound to loopback, `MARKETGUARD_SECURITY_ENABLED=false` disables the HTTP Basic prompt for reads. Case status and note writes still require both CSRF protection and an operator token. Generate a long random value, store it with the other deployment secrets, and restart the app after setting it:

```bash
MARKETGUARD_SECURITY_ENABLED=false
MARKETGUARD_OPERATOR_TOKEN="$(openssl rand -hex 32)"
```

The dashboard prompts once after an unauthenticated write, stores the token in browser `localStorage`, and sends it as `X-Operator-Token`. The server records the authenticated principal `operator` as the actor/author; request bodies cannot supply that value. An empty token is fail-closed: reads remain available, but writes return `403`. An incorrect token returns `401`.

Rotate a suspected exposed token in the secret manager, restart the app, and clear the old browser value with `localStorage.removeItem("marketguard.operatorToken")` before the next write. Treat the header as a bearer credential and use HTTPS whenever traffic leaves the host.

Loopback mode intentionally leaves read endpoints, including `/api/audit`, anonymous. Never combine it with a public port binding or an unauthenticated reverse proxy/tunnel: the token protects write integrity, not read confidentiality. The default remains `true`, and the Compose smoke test always verifies the HTTP Basic authenticated mode.

## Signal price paths and full-universe runs

Every stock signal gets a permanent price path in `signal_price_point`: for each `outcome.path-offsets` point (default 5, 15, 30 and 60 minutes) the base price at the signal, the price at that point, and the scanned-universe and same-sector median moves over the same window. Price snapshots are deleted after `maintenance.snapshot-retention` (`SNAPSHOT_RETENTION`, 90 minutes by default; 9 hours during a validation run so the after-close check can see the whole session), so this table is the only data that lets you re-check signals later with other horizons or thresholds. Rows are raw values, not verdicts. A row with an empty `price` means no snapshot was found for that point; the CSV export also lists stock signals that have no point at all, as one line with empty point columns. Signals whose snapshots were already deleted (for example during downtime) get no row; nothing is backfilled or estimated. The app refuses to start if the retention is shorter than the latest point plus two match windows and one measure interval.

Download the data as CSV (one line per signal and point, last `days` KST days including today, `days=1..366`, default 31):

```bash
# on the app host; add -u <admin user> (curl prompts for the password) when login is enabled
curl --fail -o price-paths.csv "http://127.0.0.1:5050/api/analytics/price-paths.csv?days=31"
```

Market and sector columns are filled only when enough stocks were scanned in the same window (`outcome.benchmark-minimum-samples`, 30; `outcome.sector-minimum-samples`, 5). The bundled 35-symbol seed rarely meets either, so an evaluation run should scan the full market:

1. From KRX 정보데이터시스템 download the all-listed-stocks basic information CSV and the industry classification CSV. Both CP949 and UTF-8 files are accepted.
2. Put them in `./data` (or `MARKETGUARD_DATA_DIR`) as `symbols.csv` and `sectors.csv`. Compose mounts the folder read-only at `/data`; `data/*` is git-ignored.
3. Set `SCAN_SYMBOLS_FILE=/data/symbols.csv`, `OUTCOME_SECTORS_FILE=/data/sectors.csv` and `COLLECTOR_ENABLED=true` with Toss credentials, then restart.
4. Check `marketguard_collector_scan_duration_seconds`. A full scan sends about one price request per 200 symbols per cycle; if a scan takes longer than `collector.poll-interval-ms` (30 s), raise the interval rather than the rate limits.
5. Run for at least 20 regular sessions (about four weeks) before reading the per-rule numbers; the analysis script refuses confirmatory verdicts for shorter runs. Fewer days mostly measures a few market regimes, and signals from the same stock and day are not independent.
6. Run it on a host with a fixed outbound IP that Toss accepts; [deploy-oci.md](deploy-oci.md) sets one up on Oracle Cloud. Follow [run-checklist.md](run-checklist.md) for the day-by-day procedure (`scripts/run_preflight.py` before the start, `scripts/run_status.sh` after each session). Before starting, fill in the run record in [analysis-plan.md](analysis-plan.md) (dates, commit, detection settings) and keep the settings fixed. After the last session, wait until the recorder has written every point: the longest `outcome.path-offsets` value plus the match window and one measure interval after the close (16:35 KST with the defaults), or simply the next day. Then download the CSV for the whole run and evaluate it only as the plan specifies:

   ```bash
   # registered first and last session dates (KST); DAYS = calendar days from FROM to today (KST), inclusive
   # SESSIONS = full collection days per the run record; EXCLUDE = one --exclude-day per logged outage day
   FROM=2026-12-01 TO=2026-12-29 DAYS=40 SESSIONS=20
   EXCLUDE=()   # e.g. EXCLUDE=(--exclude-day 2026-12-08)
   # on the app host; add -u <admin user> (curl prompts for the password) when login is enabled
   curl --fail -o price-paths.csv "http://127.0.0.1:5050/api/analytics/price-paths.csv?days=$DAYS"
   mkdir -p docs/results
   python3 scripts/analyze_price_paths.py --from "$FROM" --to "$TO" --sessions "$SESSIONS" "${EXCLUDE[@]}" price-paths.csv \
     > "docs/results/$TO.md"
   ```

With roughly 2,500 symbols scanned every 30 seconds, the 90-minute snapshot window holds about 450k rows; the price path adds four rows per stock signal and is never pruned automatically. Back it up with the database (see below).

## Health and metrics

- `/actuator/health/liveness`: process only; anonymous for container orchestration
- `/actuator/health/readiness`: application readiness plus database; anonymous
- `/actuator/prometheus`: authenticated by default; anonymous only when security is explicitly disabled

Key custom metrics:

- `marketguard_collector_scan_duration_seconds{outcome=...}`
- `marketguard_collector_prices_processed_total{scan=...}`
- `marketguard_collector_batch_failures_total`
- `marketguard_collector_item_failures_total`
- `marketguard_anomalies_recorded_total`
- `marketguard_detections_by_rule_total{rule}`
- `marketguard_rule_evaluation_duration_seconds{rule}`
- `marketguard_rule_evaluations_total{rule,outcome}`
- `marketguard_cases_created_total`, `marketguard_cases_merged_total`, `marketguard_cases_reactivated_total`
- `marketguard_cases_by_status{status}`, `marketguard_cases_by_score{bucket}`
- `marketguard_cases_status_transitions_total{target}`, `marketguard_cases_notes_created_total`
- `marketguard_toss_http_duration_seconds{client,endpoint,outcome}`
- `marketguard_toss_retries_total{client}`
- `marketguard_toss_circuit_transitions_total{client,transition}`
- `marketguard_toss_rate_limit_rejected_total{group}`

Recommended alerts are sustained readiness failure, any repeated batch/item failure increase, no successful scan during an expected open-market window, JVM memory pressure, PostgreSQL disk growth, and repeated `429`/circuit-open logs.

## Backup and restore

Create a compressed logical backup before upgrades:

The database name and user are read inside the container, so this works on a host where they exist only in `.env`. The second command fails if the dump is empty or unreadable; check it before copying the file anywhere.

```bash
dump="marketguard-$(date +%Y%m%d-%H%M%S).dump"
docker compose exec --no-TTY postgres sh -c \
  'exec pg_dump --format=custom --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"' > "$dump"
docker compose exec --no-TTY postgres pg_restore --list < "$dump" > /dev/null && echo "backup ok: $dump"
```

Store the dump encrypted outside the Docker host. Test restores regularly on an isolated database:

```bash
docker compose exec --no-TTY postgres sh -c 'exec createdb --username "$POSTGRES_USER" marketguard_restore'
docker compose exec --no-TTY postgres sh -c \
  'exec pg_restore --exit-on-error --clean --if-exists --username "$POSTGRES_USER" --dbname marketguard_restore' \
  < marketguard.dump
```

Never test a restore against the live database.

## Upgrade and rollback

1. Back up the database and record the current image digest.
2. Run `./gradlew clean check bootJar`, image build, and Compose smoke.
3. Deploy one app replica and wait for readiness.
4. Verify Flyway history, application logs, authenticated metrics, and collector state.

Migrations are forward-only. Application rollback is safe only when the older binary accepts the migrated schema. If not, restore the pre-upgrade backup into a new database and repoint the app; do not hand-edit Flyway history.

## Incident actions

- Suspected credential leak: disable/revoke the credential, rotate it, restart the single app replica, and inspect audit/access logs.
- Upstream outage/429: leave bounded retry/circuit behavior in place; do not shorten limits. The collector isolates failed batches and calendar failure stops scans.
- Token issuance rejected (`marketguard_toss_http_duration_seconds_count{client="auth",outcome="403"}`, log line "토스 토큰 발급 실패(N회 연속, HTTP 403)"): almost always an outbound IP that is not registered with Toss, or a revoked key. After a failed issue every caller fails fast without contacting Toss for 30 seconds, doubling to 10 minutes, or as long as Toss's `Retry-After` asks up to 24 hours. The warning log states the actual wait in seconds; fix the registration and wait for the next attempt rather than restarting in a loop.
- Database unavailable: readiness becomes unhealthy; restore connectivity before restarting repeatedly.
- Alert flood: disable the collector, preserve DB evidence, inspect thresholds/timestamps, then re-enable. Durable cooldown survives restart.
- Case backlog: inspect `marketguard_cases_by_status`, filter old `NEW`/`REVIEWING` cases, and review per-rule dismissed ratios before changing any threshold. The application never tunes thresholds automatically.
- Version conflict: reload the case detail and reapply the operator decision to the new version; never bypass optimistic locking.
- Disk growth: snapshots retain 90 minutes by default; signal price paths are permanent. Audit logs are kept forever unless `AUDIT_RETENTION` (`maintenance.audit-retention`, minimum one day, e.g. `365d`) is set; each cleanup writes its own `AUDIT_LOG_PRUNED` audit record. Anomaly records stay because cases reference them; define their retention (archive or export) before long-running production use.
- Institutional-flow polling is configured with `collector.institutional-flow-poll-interval-ms`; its six-hour directional cooldown is independent from the per-stock cooldown.

## Shutdown

```bash
docker compose stop --timeout 30 app
docker compose down
```

Do not add `--volumes` during normal shutdown. That flag deletes the PostgreSQL volume and is reserved for disposable smoke environments.
