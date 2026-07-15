# Security policy

## Scope

MarketGuard is strictly read-only. A report involving an order, transfer, account, or fund-movement capability is a critical scope violation. Do not add account identifiers or trading endpoints to this repository.

## Reporting

Report suspected vulnerabilities privately to the repository owner. Do not open a public issue containing credentials, tokens, exploit payloads, private market data, or infrastructure details.

Include the affected commit, reproduction conditions, impact, and a sanitized proof of concept. Revoke exposed Toss, database, or operator credentials immediately; removing them from Git history does not make them safe again.

## Deployment requirements

- Use the `prod` profile only behind a TLS reverse proxy and network ACL.
- Supply database, operator, and optional Toss secrets through the deployment platform's secret store.
- Never commit `.env`, `application-local.yml`, logs, database dumps, or access tokens.
- Use one application replica per Toss client credential.
- Keep `/actuator/prometheus`, the dashboard, API, and WebSocket endpoints authenticated.
- Patch pinned base-image digests and dependency versions deliberately after CI and smoke verification.

Supported security fixes target the latest `main` branch only.
