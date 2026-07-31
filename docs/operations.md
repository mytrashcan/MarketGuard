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

For a single-user machine that remains bound to loopback, `MARKETGUARD_SECURITY_ENABLED=false` disables the HTTP Basic prompt. Never combine this setting with a public port binding or an unauthenticated reverse proxy. The default remains `true`, and the Compose smoke test always verifies the authenticated mode.

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

```bash
docker compose exec --no-TTY postgres \
  pg_dump --format=custom --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  > "marketguard-$(date +%Y%m%d-%H%M%S).dump"
```

Store the dump encrypted outside the Docker host. Test restores regularly on an isolated database:

```bash
docker compose exec --no-TTY postgres createdb --username "$POSTGRES_USER" marketguard_restore
docker compose exec --no-TTY postgres \
  pg_restore --exit-on-error --clean --if-exists \
  --username "$POSTGRES_USER" --dbname marketguard_restore < marketguard.dump
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
- Database unavailable: readiness becomes unhealthy; restore connectivity before restarting repeatedly.
- Alert flood: disable the collector, preserve DB evidence, inspect thresholds/timestamps, then re-enable. Durable cooldown survives restart.
- Case backlog: inspect `marketguard_cases_by_status`, filter old `NEW`/`REVIEWING` cases, and review per-rule dismissed ratios before changing any threshold. The application never tunes thresholds automatically.
- Version conflict: reload the case detail and reapply the operator decision to the new version; never bypass optimistic locking.
- Disk growth: snapshots retain one hour by default. Define environment-specific anomaly/audit retention before long-running production use.
- Institutional-flow polling is configured with `collector.institutional-flow-poll-interval-ms`; its six-hour directional cooldown is independent from the per-stock cooldown.

## Shutdown

```bash
docker compose stop --timeout 30 app
docker compose down
```

Do not add `--volumes` during normal shutdown. That flag deletes the PostgreSQL volume and is reserved for disposable smoke environments.
