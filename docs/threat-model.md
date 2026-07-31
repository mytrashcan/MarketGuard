# Threat model

## Assets and trust boundaries

Assets are Toss OAuth credentials/tokens, database and operator credentials, stored market/anomaly/audit data, upstream quota, and the integrity/availability of alerts.

Trust boundaries are:

1. Operator browser to HTTP, Actuator, and SockJS/STOMP.
2. Application to Toss OAuth and market-data APIs over TLS.
3. Application to PostgreSQL on the private Compose network.
4. CI/build to Gradle/Maven, GitHub Actions, base images, and browser CDNs.
5. Operator-supplied environment variables and symbol files.

## Threats and controls

| Threat | Primary controls | Residual risk |
|---|---|---|
| Unauthorized dashboard/API access | Production Basic auth, loopback host bind, TLS proxy requirement | Loopback reads are intentionally anonymous and must not be publicly routed |
| Anonymous case mutation (MG-01) | Status/note routes require authentication in every mode; loopback uses a fail-closed operator token plus CSRF | A stolen browser/token can write until the token is rotated |
| Cross-origin SockJS access | Exact origin allowlist; no wildcard | Reverse proxy must preserve the correct `Origin` |
| DOM/stored XSS | Escaped API-derived values, constrained enums, CSP, SRI | Inline legacy dashboard code requires `'unsafe-inline'` |
| Credential disclosure | No defaults, fail-fast validation, sanitized exceptions/logs, ignored local files, non-root image | Environment variables remain visible to privileged host operators |
| Quota/resource exhaustion | Bounded inputs, peer-address API limit, upstream rate groups, timeout/retry limits, fixed-delay schedules | A reverse proxy appears as one peer unless rate limiting is also enforced at that trusted proxy |
| False alerts from stale/bad data | Official timestamp, fail-closed calendar, domain validation, decimal policy | Data can still be semantically wrong at the upstream source |
| Duplicate alerts | Atomic durable cooldown and committed-before-publish ordering | Browser delivery itself is best-effort |
| Database loss/compromise | Required random password, no host DB port, named volume, Flyway, backup runbook | Backups are an operator responsibility and must be encrypted externally |
| Supply-chain substitution | Pinned Actions and container digests, wrapper validation, SRI, CI rebuild | Maven dependencies are versioned but not checksum-locked |

## Explicit non-goals

- No order placement, cancellation, account balance, position, transfer, or fund movement.
- No public anonymous dashboard. Loopback mode deliberately permits local anonymous reads, including `/api/audit`, but is not a public deployment mode.
- No claim of regulatory completeness, market-abuse proof, or investment suitability.
- No horizontal scaling with a shared Toss client credential.
