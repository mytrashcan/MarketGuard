# Operator API

Production endpoints require HTTP Basic authentication by default. A loopback-only personal deployment may explicitly set `MARKETGUARD_SECURITY_ENABLED=false`; reads then remain anonymous, while status and note writes require the configured `MARKETGUARD_OPERATOR_TOKEN` in `X-Operator-Token`. The server uses the authenticated principal `operator` as the review actor. All browser write requests also require the CSRF header returned by `GET /api/csrf`. Responses never contain upstream bodies or internal exception details.

## Case list

`GET /api/cases`

| Parameter | Constraint |
|---|---|
| `page` | integer, at least 0 |
| `size` | 1 to 100, default 25 |
| `status` | One or more of `NEW`, `REVIEWING`, `WATCHING`, `DISMISSED`, `ESCALATED`, `CLOSED`, comma-separated or repeated (e.g. `status=NEW,REVIEWING`); matches any |
| `ruleType` | a documented rule enum |
| `severity` | `INFO`, `WARNING`, `CRITICAL` |
| `stockCode` | exactly six digits, `KOSPI`, or `KOSDAQ` |
| `from`, `to` | ISO-8601 instants; `from <= to` |
| `minimumScore`, `maximumScore` | 0 to 100; minimum cannot exceed maximum |
| `sort` | `lastDetectedAt`, `firstDetectedAt`, `score`, `stockCode`, `status` |
| `direction` | `asc` or `desc` |

The stable page envelope is `content`, `page`, `size`, `totalElements`, and `totalPages`. The list reads only case rows and does not load signal collections, preventing N+1 queries.

## Case detail and review

- `GET /api/cases/{id}` returns case summary, score contributions, structured signals, notes, and status history.
- `PATCH /api/cases/{id}/status` accepts `status`, current `version`, optional `reason`, and optional `detail` up to 500 characters.
- `POST /api/cases/{id}/notes` accepts a non-blank `note` up to 2,000 characters.

Terminal states (`DISMISSED`, `CLOSED`) require a closure reason. A stale version returns:

```json
{"code":"CASE_VERSION_CONFLICT","message":"The case was modified; reload and try again"}
```

The authenticated principal becomes the reviewer/author. Request bodies cannot choose another author. The UI renders all stored text through HTML escaping.

## Evidence and context

- `GET /api/anomalies?limit=50` remains backward compatible and now adds structured evidence fields.
- `GET /api/anomalies/{id}` returns one structured signal.
- `GET /api/stocks/{code}/context?count=120` returns quote/candles and explicit unavailable context. It never fabricates news, disclosures, industry movement, or index returns.
- `GET /api/analytics/rules` returns occurrence and case counts, dismissed/escalated percentages, and average completed review time.
- `GET /api/analytics/signals?days=14` (`days=1..31`, default 14) returns, for the last `days` KST calendar days including today:
  - `daily`: signals, critical signals and distinct symbols per day, with quiet days as zeros.
  - `rules`: per rule, signal count, measured count, `directional` sample size, `followedRatePercent` (share of directional measured signals whose price moved the signal's way after `horizonMinutes`; 50% is chance) and `medianAbsChangePercent`. Rates are `null` when there is no sample.
  - `coverage`: counts of `measured`, `noPriceData`, `notApplicable` and `pending` outcomes.
- Signal objects (`/api/anomalies`, case `signals`) include `outcomeStatus` (`MEASURED`, `NO_PRICE_DATA`, `NOT_APPLICABLE`, or `null` while pending), `outcomeChangePercent`, `outcomeFollowedDirection` and `outcomeMeasuredAt`.

## Stable errors

| Status | Code | Meaning |
|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | supplied operator token is invalid or production authentication is missing |
| 403 | — | CSRF token or configured loopback write authentication is missing |
| 400 | `INVALID_REQUEST` | malformed, out-of-range, or invalid transition input |
| 404 | `CASE_NOT_FOUND` | requested case does not exist |
| 404 | `ANOMALY_NOT_FOUND` | requested signal (`/api/anomalies/{id}`) does not exist |
| 409 | `CASE_VERSION_CONFLICT` | optimistic version is stale |
| 502 | `MARKET_DATA_UNAVAILABLE` | permanent upstream response failure |
| 503 | `MARKET_DATA_UNAVAILABLE` | timeout, rate limit, or open circuit |
| 500 | `INTERNAL_ERROR` | unexpected failure with a correlation error ID |
