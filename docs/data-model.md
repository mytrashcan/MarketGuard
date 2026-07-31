# Surveillance data model

## Tables

- `anomaly_record`: immutable signal and structured evidence, including the persisted stock/market name, official market timestamp, evaluation timestamp, decimal observed/baseline/threshold/deviation values, direction, tags, checks, caution, and bounded measurement JSON.
- `surveillance_case`: aggregate summary, composite score, attention level, status, first/last detection, reviewer, closure fields, tags, and optimistic `version`.
- `case_note`: append-only operator notes.
- `case_status_history`: append-only automatic and operator state changes.
- `case_group_lock`: one row per stock or supported market symbol used only to serialize case selection/creation.
- `anomaly_cooldown`: independent `(instrument, rule)` emission guard.

JSON text columns are bounded by application item counts and validated as arrays by PostgreSQL constraints. Frequently used filters remain typed columns with indexes. Case list queries never fetch child collections; detail queries load signals, notes, and history in three bounded queries.

Migration V4 backfills every existing anomaly into a single-signal case. Because old rows did not retain raw values, the migration labels them as legacy evidence instead of fabricating numbers. Their stock name safely falls back to the stock code until a live reference lookup can enrich display.

Migration V5 extends only the signal-owning instrument constraints to accept `KOSPI` and `KOSDAQ` in addition to six-digit stocks. Price snapshots remain stock-only. Market-level institutional signals therefore share the review workflow without pretending to belong to an individual stock.

## Extension models

`RelativeMovementProvider` is the inward-facing port for industry average, market index, then monitored-universe comparison. It currently returns unavailable because the required aligned time series is not retained.

`IntradayBaselineCalculator` uses configurable time buckets and calculates mean, median, and population standard deviation without `double`/`float`. It requires a minimum sample count, falls back to the supplied broader sample only when sufficient, and otherwise reports insufficient data. Production caching/storage is deferred until multi-day session-aware history is available.
