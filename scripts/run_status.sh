#!/usr/bin/env bash
# Daily health check for the pre-registered run (docs/run-checklist.md). Reads the Compose PostgreSQL
# directly, needs no operator credentials, changes nothing, and exits non-zero if any query fails.
# Run from the repository root after the close and before 18:00 KST (snapshots are kept 9h during the run),
# passing the scan universe size recorded before the run (run record, from scripts/run_preflight.py):
#   ./scripts/run_status.sh 2650
# It deliberately shows no follow rates: do not judge rules during the run.
set -euo pipefail

universe="${1:-}"
if [[ ! "$universe" =~ ^[1-9][0-9]*$ ]]; then
  echo "usage: $0 <registered scan universe size from the run record>" >&2
  exit 2
fi

# Database name and user come from the running container, exactly as Compose resolved them.
docker compose exec --no-TTY postgres sh -c \
  'exec psql --quiet --no-psqlrc --set=ON_ERROR_STOP=1 --set=universe="$1" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB"' \
  sh "$universe" <<'SQL'
\pset footer off
SET TIME ZONE 'Asia/Seoul';

\echo '== Today''s regular session, in 5-minute buckets (09:00-15:30 KST)'
\echo '   A full session has data in every bucket and at least 90% of the registered universe in almost all of them.'
WITH day AS (
    SELECT date_trunc('day', now()) AS start
), buckets AS (
    SELECT generate_series(start + interval '9 hours',
                           least(start + interval '15 hours 25 minutes', now() - interval '5 minutes'),
                           interval '5 minutes') AS bucket
    FROM day
), per_bucket AS (
    SELECT bucket,
           (SELECT count(DISTINCT stock_code) FROM price_snapshot
            WHERE captured_at >= bucket AND captured_at < bucket + interval '5 minutes') AS symbols
    FROM buckets
)
SELECT count(*)                                                        AS buckets_so_far,
       count(*) FILTER (WHERE symbols > 0)                             AS with_data,
       count(*) FILTER (WHERE symbols >= 0.9 * :universe)              AS near_full_universe,
       :universe                                                       AS registered_universe,
       max(symbols)                                                    AS peak_symbols,
       (SELECT min(captured_at) FROM price_snapshot, day WHERE captured_at >= day.start) AS first_snapshot_today,
       coalesce(string_agg(to_char(bucket, 'HH24:MI'), ' ' ORDER BY bucket) FILTER (WHERE symbols = 0), '-')
                                                                       AS empty_buckets
FROM per_bucket;

\echo '== Collection right now'
SELECT max(captured_at)                                                           AS latest_snapshot,
       date_trunc('second', now() - max(captured_at))                             AS age,
       count(DISTINCT stock_code) FILTER (WHERE captured_at > now() - interval '90 minutes') AS symbols_last_90m
FROM price_snapshot;

\echo '== Signals per KST day (last 7 days)'
SELECT detected_at::date AS day,
       count(*) FILTER (WHERE stock_code NOT IN ('KOSPI', 'KOSDAQ')) AS stock_signals,
       count(*) FILTER (WHERE stock_code IN ('KOSPI', 'KOSDAQ'))     AS market_signals,
       count(DISTINCT stock_code)                                    AS symbols
FROM anomaly_record
WHERE detected_at > now() - interval '7 days'
GROUP BY 1 ORDER BY 1 DESC;

\echo '== Price paths of today''s stock signals older than 70 minutes'
\echo '   usable_30m only counts signals before 14:57, when the full 3-minute matching window fits before the close.'
SELECT count(*)                                                       AS signals,
       count(*) FILTER (WHERE offsets = 4)                            AS all_offsets_recorded,
       count(*) FILTER (WHERE offsets = 0)                            AS none_recorded,
       count(*) FILTER (WHERE detected_at::time < time '14:57')       AS before_1457,
       count(*) FILTER (WHERE detected_at::time < time '14:57' AND usable_30m) AS usable_30m
FROM (
    SELECT a.id, a.detected_at,
           count(p.offset_minutes) AS offsets,
           bool_or(p.offset_minutes = 30 AND p.base_price IS NOT NULL AND p.price IS NOT NULL
                   AND p.market_change_percent IS NOT NULL) IS TRUE AS usable_30m
    FROM anomaly_record a LEFT JOIN signal_price_point p ON p.anomaly_id = a.id
    WHERE a.stock_code NOT IN ('KOSPI', 'KOSDAQ')
      AND a.detected_at::date = now()::date
      AND a.detected_at < now() - interval '70 minutes'
    GROUP BY a.id, a.detected_at
) per_signal;
SQL
