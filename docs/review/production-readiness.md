# Production-readiness resolution

Baseline: `origin/main` at `ffb16f2`. The immutable evidence is in `initial-findings.md`.

## Resolution summary

| Finding | Status | Resolution evidence |
|---|---|---|
| SEC-01, SEC-02 | Resolved | Authenticated production surface, anonymous probes only, strict Origin, loopback binding, required secrets, private persistent PostgreSQL |
| API-01..04 | Resolved with documented scale limit | Bounded HTTP clients, classified retry, `Retry-After`, rate groups, safe errors, official timestamps, validated single-flight token; one replica documented |
| DOM-01..03 | Resolved | Framework-free detection core, architecture guard, `BigDecimal` policy, validated values/config, per-rule isolation and unique rule types |
| REL-01..03 | Resolved for one replica | Atomic PostgreSQL cooldown, commit-before-notify, injected clocks, fail-closed calendar, fixed-delay bounded scheduler and overlap guard |
| WEB-01 | Resolved | Strict symbol/interval/count/limit validation, safe HTTP mapping, authenticated per-client API rate limit |
| DB-01, TST-01 | Resolved | Flyway on empty PostgreSQL, three migrations, schema validation, non-skipping Testcontainers, repository/cooldown concurrency tests |
| PKG-01 | Resolved | Digest-pinned multi-stage image, non-root runtime, healthcheck, read-only Compose service, private volume-backed DB, smoke script |
| OBS-01, SEC-03, DOC-01, ARCH-01 | Resolved or bounded | Prometheus/probes/runbooks, escaped dynamic HTML, CSP/SRI, inward dependencies and explicit residual risks |
| OPS-01 | Partially resolved | Snapshot retention is configurable and backup/restore documented; anomaly/audit retention remains deployment-specific |
| CI-01 | Repository work resolved; external blocker remains | Minimal permissions, concurrency, timeout, pinned actions, wrapper validation, test/image/Compose gates. GitHub billing prevents current hosted runs from starting |
| QLT-01 | Resolved | Executable wrapper, wrapper validation, `-Xlint:all -Werror`, architecture test, 65% JaCoCo line gate, reproducible archives |

## Readiness decision

Conditionally Ready for a single-replica, TLS-proxied deployment after operators provide real secrets, configure backups/retention, fix the GitHub Actions billing/spending blocker, rerun CI, and enable branch protection. It is not approved for anonymous public exposure, horizontal scaling with one Toss client, or any trading/fund capability.
