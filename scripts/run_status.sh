#!/usr/bin/env bash
# Daily health check for the pre-registered run. Reads the Compose PostgreSQL directly, so it needs no
# operator credentials and changes nothing. Run from the repository root:  ./scripts/run_status.sh
# Look for: a fresh latest snapshot during market hours, a full universe per scan, and price points
# keeping up with signals. Do not use it to look at per-rule follow rates during the run.
set -euo pipefail

db="${POSTGRES_DB:-}"
user="${POSTGRES_USER:-}"
if [[ -f .env ]]; then
  db="${db:-$(sed -n 's/^POSTGRES_DB=//p' .env | tr -d "\"'")}"
  user="${user:-$(sed -n 's/^POSTGRES_USER=//p' .env | tr -d "\"'")}"
fi
db="${db:-marketguard}"
user="${user:-marketguard}"

docker compose exec --no-TTY postgres psql --quiet --no-psqlrc --username "$user" --dbname "$db" <<'SQL'
\pset footer off
SET TIME ZONE 'Asia/Seoul';

\echo '== Collection (snapshots are kept 90 minutes)'
SELECT max(captured_at)                                   AS latest_snapshot,
       date_trunc('second', now() - max(captured_at))     AS age,
       count(DISTINCT stock_code)                         AS symbols_last_90m,
       count(*) FILTER (WHERE captured_at > now() - interval '5 minutes')  AS rows_last_5m
FROM price_snapshot;

\echo '== Signals per KST day (last 7 days, stock signals vs market-wide)'
SELECT detected_at::date AS day,
       count(*) FILTER (WHERE stock_code NOT IN ('KOSPI', 'KOSDAQ')) AS stock_signals,
       count(*) FILTER (WHERE stock_code IN ('KOSPI', 'KOSDAQ'))     AS market_signals,
       count(DISTINCT stock_code)                                    AS symbols
FROM anomaly_record
WHERE detected_at > now() - interval '7 days'
GROUP BY 1 ORDER BY 1 DESC;

\echo '== Price path recording (stock signals older than 70 minutes should have all 4 points)'
SELECT count(*)                                                       AS signals,
       count(*) FILTER (WHERE points = 4)                             AS complete,
       count(*) FILTER (WHERE points = 0)                             AS none_recorded,
       count(*) FILTER (WHERE with_price < points)                    AS with_missing_prices
FROM (
    SELECT a.id, count(p.offset_minutes) AS points, count(p.price) AS with_price
    FROM anomaly_record a LEFT JOIN signal_price_point p ON p.anomaly_id = a.id
    WHERE a.stock_code NOT IN ('KOSPI', 'KOSDAQ')
      AND a.detected_at::date = now()::date
      AND a.detected_at < now() - interval '70 minutes'
    GROUP BY a.id
) per_signal;
SQL
