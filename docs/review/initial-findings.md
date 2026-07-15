# Initial production-readiness findings

Review baseline: `ffb16f2` (`origin/main`, 2026-07-15 KST).

This is the immutable pre-change finding set. Resolution details are maintained in the final review report. MarketGuard is a read-only, rule-based market-data analysis and alerting project; it is not an exchange surveillance system and must not be used as investment advice.

## Reproduced baseline

- `./gradlew clean test --no-daemon` failed with `permission denied` because `gradlew` was tracked as mode `100644`.
- `bash ./gradlew clean test --no-daemon` could not start on the review host because no bootstrap JRE was installed.
- The same task in a JDK 17 container reported success only because `PriceSnapshotRepositoryIntegrationTest` was skipped when Testcontainers could not access Docker as the unprivileged container user.
- With Docker access enabled, `PriceSnapshotRepositoryIntegrationTest` failed at line 38 with `TransactionRequiredException`; `clean build` consequently failed (`21 tests completed, 1 failed`).
- `docker build -t marketguard:baseline .` succeeded, but the Dockerfile explicitly skipped tests. The resulting image ran as root, had no healthcheck, and was approximately 162 MB.
- A baseline container returned HTTP 200 without authentication for `/`, `/api/audit`, and `/actuator/metrics`; `/h2-console` redirected to its UI; `/ws/info` accepted `Origin: https://evil.example`.
- The latest GitHub Actions run did not start any step. Its check annotation says account payments failed or the spending limit must be increased. This external account state cannot be repaired in repository code.
- GitHub reports `main` as unprotected.

## Findings

| ID | Priority | Evidence before changes | Impact | Reproduction | Recommended resolution |
|---|---|---|---|---|---|
| SEC-01 | P0 Blocking | `DashboardController.java:52-85`, `application.yml:24-28`, `WebSocketConfig.java:25-28` | Dashboard data, audit records, metrics, H2, and WebSocket topics are reachable without authentication; arbitrary web origins can establish SockJS sessions. | Start the baseline image, then request `/api/audit`, `/actuator/metrics`, `/h2-console`, and `/ws/info` with an untrusted `Origin`. | Require authentication in production, expose only liveness/readiness anonymously, disable H2 outside local development, and allow an explicit origin list. |
| SEC-02 | P0 Blocking | `application-prod.yml:5-8`, `compose.yaml:7-14,24-33` | A default database credential is usable, PostgreSQL is published on all host interfaces, and no durable volume is defined. A public host risks compromise and data loss. | Run `docker compose config` with placeholder Toss credentials and inspect ports, passwords, and volumes. | Remove all production credential fallbacks, keep PostgreSQL internal, require secrets, add a named volume, and bind the app to loopback/reverse proxy by default. |
| API-01 | P1 High | `RestClientConfig.java:18-29`, `TossTokenClient.java:25-44` | Missing connect/read timeouts can indefinitely occupy the single scheduler thread and block shutdown or all collection jobs. | Point the base URL at a server that accepts connections without responding. | Use bounded connect/read timeouts for both token and market clients and test timeout behavior with a fake server. |
| API-02 | P1 High | `ResilienceConfig.java:34-41`, `TossMarketDataClient.java:41-44` | Retrying every `Exception` repeats permanent 4xx failures, amplifies load, ignores `Retry-After`, and makes the circuit breaker count attempts ambiguously. | Return 400 or 429 from a fake server and count requests. | Retry only network, 429, and 5xx failures; honor `Retry-After`; use exponential backoff with jitter; document breaker/retry order and instrument both. |
| API-03 | P1 High | `PricesResponse.java:12-19`, `TossMarketDataClient.java:61-67` | The official market timestamp is discarded and replaced with local wall-clock time, weakening temporal accuracy and stale/duplicate handling. | Map an official response containing a known timestamp and inspect the saved snapshot. | Map the response timestamp, reject malformed values, and inject `Clock` only for an explicit missing-timestamp fallback. |
| API-04 | P1 High | `TossTokenManager.java:18-55` | Non-positive or too-short `expires_in` values create immediate refresh loops; wall-clock calls are nondeterministic. Reissuing a Toss token invalidates the previous token, so multiple instances using one client can invalidate each other. | Return `expires_in <= 30`, then call `getAccessToken` repeatedly or concurrently. | Validate token type/value/lifetime, inject `Clock`, retain single-flight refresh, recover cleanly from failures, and document/enforce the single-instance credential boundary. |
| DOM-01 | P1 High | `DetectionContext.java:3,11-17`, `PriceSpikeRule.java:8`, `TossMarketDataClient.java:8` | The detection core depends on a JPA entity, while persistence records depend back on detection models. This creates an architectural cycle and makes persistence details part of rule contracts. | Inspect imports between `detection` and `domain`. | Introduce a persistence-free market-price value object and map at repository/client boundaries; add an architecture test. |
| DOM-02 | P1 High | `BoardItem.java:33-36`, `PriceSpikeRule.java:37-53`, `PriceLimitRule.java:47-55` | Financial ratios use binary floating point without an explicit scale/rounding policy; invalid or negative prices and invalid thresholds are not rejected. | Exercise decimal boundary values and invalid configuration. | Use `BigDecimal` with documented precision/rounding, validate domain inputs and configuration, and add exact boundary tests. |
| DOM-03 | P1 High | `RuleEngine.java:27-31` | One buggy rule aborts all remaining rule evaluation; duplicate rule types are not rejected. | Configure one throwing rule before a healthy rule. | Isolate rule exceptions, emit a failure metric/log without sensitive data, reject duplicate types at startup, and test composition. |
| REL-01 | P1 High | `MarketDataCollector.java:60-61,155-177` | Cooldown state is in memory, updated before durable persistence, lost on restart, and not safe across multiple instances. Notification failure can also leave persistence and publication inconsistent. | Restart during an active cooldown or run two instances on one DB. | Make recording/deduplication transactional and durable or explicitly enforce a supported single-instance mode; publish only after a committed record. |
| REL-02 | P1 High | `MarketSession.java:32-69`, `VolumeSurgeRule.java:46-48`, `InvestmentWarningRule.java:47` | Static wall-clock reads prevent deterministic KST boundary tests. Calendar failure opens the market on ordinary weekdays, which can process stale holiday data and produce false signals. | Fail the calendar API on a weekday holiday and evaluate the gate. | Inject `Clock`, fail closed by default when the official calendar is unavailable, define end-time semantics, and test KST boundaries/holidays. |
| REL-03 | P1 High | `MarketDataCollector.java:87-167`, `BoardDataService.java:69-84`, `PriceStreamScheduler.java:30-39` | No execution lock or explicit scheduler pool policy exists; long serial API loops can starve other jobs, and multiple instances duplicate calls and alerts. | Delay external responses and observe all scheduled work on the default scheduler. | Configure a bounded scheduler, prevent overlapping executions, add execution IDs/metrics, isolate per-symbol failures, and document the supported instance count. |
| WEB-01 | P1 High | `DashboardController.java:58-66,71-85` | Stock codes, intervals, and counts are unvalidated. The controller turns all upstream failures into empty 200 responses, hiding incidents and enabling authenticated external-API amplification. | Request malformed symbols, unsupported intervals, negative or very large counts. | Add strict validation and bounded pagination, return stable sanitized errors, distinguish unavailable upstream data, and rate-limit expensive read endpoints. |
| DB-01 | P1 High | `application-prod.yml:10-12`, `V1__init.sql`, JPA mappings | Production disables Hibernate schema validation, constraints permit malformed market data, and the migration is not exercised by the existing PostgreSQL test. | Run the integration test with Testcontainers and inspect Flyway history. | Enable `ddl-auto=validate`, run Flyway against an empty real PostgreSQL database, add CHECK/UNIQUE/index constraints where justified, and test all repositories. |
| TST-01 | P1 High | `PriceSnapshotRepositoryIntegrationTest.java:20-38` | `disabledWithoutDocker=true` silently removes the only real-PostgreSQL test; when enabled, it fails. CI can appear green without validating the production database. | Run the test with and without Docker socket access. | Make Docker absence fail the integration job, fix transaction boundaries, activate Flyway, and publish test reports. |
| PKG-01 | P1 High | `Dockerfile:1-18`, `compose.yaml` | Mutable base tags, root runtime, no healthcheck, no signal/graceful-shutdown policy, skipped tests, no DB volume, and no restart policy make the package unsafe to operate. | Inspect the image config and Compose model. | Pin maintained base images, run non-root, add healthcheck/graceful shutdown, make CI prove tests before image build, persist PostgreSQL, and add credential-free local smoke Compose. |
| CI-01 | P1 High | `.github/workflows/ci.yml` plus Actions run `27921858105` | Current Actions cannot start because of billing/spending state. The workflow also lacks minimal permissions, timeouts, concurrency cancellation, wrapper validation, coverage, security analysis, image scan, and diagnostic artifacts. | Read the run check annotation and workflow. | Repair repository workflow gates; document the external billing blocker; add minimal CodeQL/dependency/container checks and branch-protection recommendations. |
| OBS-01 | P2 Medium | Existing Actuator config and scheduler/client code | Operators cannot see last successful collection, per-rule detections, upstream latency/error/retry/breaker state, scheduler duration, or execution correlation. | Inspect `/actuator/metrics` after a scan. | Add focused Micrometer metrics, execution IDs, safe structured logs, liveness/readiness, and an operations runbook. |
| SEC-03 | P2 Medium | `static/index.html:260-269,395-397,433-439` and external scripts | API-derived values are inserted with `innerHTML`; third-party scripts have no SRI or local vendoring. A compromised upstream or stored value could become script-capable content. | Store crafted text or replace a CDN response in a test environment. | Render untrusted values with `textContent`/escaping, add a restrictive CSP, and pin/vendor or integrity-check browser dependencies. |
| DOC-01 | P2 Medium | `README.md:5-8,60-87` | The project calls itself a miniature regulator/exchange surveillance system, encourages direct external HTTP exposure, and lacks security, backup, recovery, rollback, API contract, and limitation guidance. | Compare README instructions with the runtime threat model. | Reframe as reference-only rule-based detection, document non-goals and limitations, and add deployment/security/runbook/API guidance. |
| ARCH-01 | P2 Medium | Package import graph and scheduler/controller classes | Application orchestration is embedded in Spring schedulers/controllers, and core/persistence dependencies point both ways. Current Clean Architecture diagnostic score: 4/10. | Inspect imports and instantiate orchestration without Spring/DB. | Keep the modular monolith but introduce narrow ports/value objects at volatile API and persistence boundaries; avoid a rewrite or microservices. |
| OPS-01 | P2 Medium | `SnapshotCleanupScheduler.java`, audit/anomaly tables | Retention is hard-coded for snapshots and absent for audit/anomaly records; backup/restore and migration rollback are undocumented. | Inspect scheduler constants and DB growth behavior. | Externalize retention, add bounded cleanup where appropriate, and provide tested backup/restore/rollback procedures. |
| QLT-01 | P3 Low | `gradlew`, build/workflow files | The wrapper lacks executable mode and wrapper checksum validation; there is no enforced format/static-analysis baseline. | Run `./gradlew` and inspect Git mode/workflows. | Track executable mode, validate the wrapper in CI, and add a low-noise formatter/static analysis gate. |

## Initial threat model

### Assets

- Toss OAuth client credentials and short-lived access tokens.
- PostgreSQL credentials and stored price, anomaly, and audit records.
- Availability and quota of the Toss market-data API.
- Integrity of rule configuration, symbols, timestamps, and operator-visible alerts.

### Trust boundaries and entry points

1. Browser/operator to HTTP API, static dashboard, Actuator, H2 console, and WebSocket/SockJS handshake.
2. Application to Toss OAuth and market-data endpoints over TLS.
3. Application to PostgreSQL over the private Compose network.
4. CI runner and Docker build to third-party actions, Gradle/Maven artifacts, base images, and browser CDNs.
5. Host/operator supplied environment variables and symbol files to application configuration.

### Principal threats

- Unauthorized disclosure of audit/market data and operational metrics.
- Credential theft through configuration, process environment, logs, HTTP errors, or build artifacts.
- Cross-origin WebSocket use and stored/DOM XSS in the dashboard.
- Resource exhaustion or Toss quota exhaustion through expensive endpoints and scheduler overlap.
- False positives/negatives from stale timestamps, calendar failure, invalid market data, precision loss, or partial upstream failure.
- Duplicate alerts and token invalidation after restart or multi-instance execution.
- Database loss or compromise through default credentials, published ports, missing volumes, and untested migrations.
- Supply-chain compromise through mutable images/actions, unchecked wrapper artifacts, dependencies, or CDNs.

### Security posture target

Production starts only with explicit database and operator credentials; collection additionally requires explicit Toss credentials. Public data paths require authentication, health probes reveal only status, PostgreSQL remains private, browser origins are allow-listed, outbound calls are bounded and classified, logs never contain secrets, and the documented deployment assumes TLS termination at a reverse proxy. Local credential-free mode binds only to loopback, disables collection, and uses ephemeral H2 data.
