# Detection rules

All rules produce review signals, not conclusions about market abuse or investment value. Financial calculations use `BigDecimal`, scale 8, and `HALF_UP`; the UI normally displays two decimals.

## `PRICE_SPIKE`

- Purpose: find a short price move relative to recent observations.
- Input: current price and up to the configured number of previous price snapshots.
- Formula: `(current - recent mean) / recent mean * 100`.
- Boundary: detects at `abs(change) >= threshold`; critical at twice the threshold.
- Insufficient data: no previous price means no signal.
- False positives: broad market moves, open/close volatility, corporate events, sparse sampling.
- Check: volume, market/sector movement, and confirmed public events.

## `VOLUME_SURGE`

- Purpose: find a sudden one-minute trading-volume increase.
- Input: latest complete available one-minute candle and preceding candles.
- Formula: `latest volume / preceding mean volume`.
- Boundary: detects at `ratio >= multiplier`; a zero baseline or candle older than five minutes is skipped.
- Severity: currently warning; the composite score handles multi-signal priority.
- False positives: the market open/close, auctions, index rebalancing, and disclosed events.
- Check: same-time historical volume, price direction, and confirmed public events.

## `ORDERBOOK_IMBALANCE`

- Purpose: compare publicly visible pending bid and ask quantities.
- Input: sum of all returned bid levels and all returned ask levels.
- Formula: `larger total / smaller total`.
- Boundary: both sides must be positive; critical at twice the configured ratio.
- Missing data: no data and upstream failure are represented separately from zero.
- False positives: cancellations, temporary liquidity gaps, and non-executable displayed interest.
- Check: persistence across observations, actual trades, and price direction. This is not a buy/sell execution ratio.

## `PRICE_LIMIT`

- Purpose: show arrival at or proximity to the exchange-provided daily upper/lower limit.
- Input: current price and official limit prices.
- Formula: absolute percentage distance between current price and the relevant limit.
- Boundary: reaching the limit is critical; configured proximity is warning.
- Missing data: no limit response means no signal.
- False positives: publicly explained high-volatility events and normal price discovery.

## `INVESTMENT_WARNING`

- Purpose: surface an active public exchange designation.
- Input: warning type and effective dates.
- Boundary: future/expired designations and non-designation noise such as transient VI items are excluded.
- Severity: investment risk/liquidation is critical; warning/overheated is warning.
- Limitation: designation increases review relevance but says nothing about the current trade's fairness.

## `PRICE_VOLUME_SURGE`

- Purpose: prioritize a move where price and volume cross their own thresholds in the same evaluation.
- Input: the price inputs from `PRICE_SPIKE` plus the candle inputs from `VOLUME_SURGE`.
- Boundary: both components must meet their thresholds. Critical requires both to reach twice their threshold.
- Score: one configurable contribution; component rules may also contribute, with the total capped at 100.
- False positives: market/sector movement and confirmed public events. Review those before escalation.

## `INSTITUTIONAL_NET_BUY_SURGE` / `INSTITUTIONAL_NET_SELL_SURGE`

- Purpose: find unusually large institutional net buying or selling across the KOSPI or KOSDAQ market.
- Input: Toss Open API daily KRX investor-trading records. The institution total includes seven reported categories: financial investment, insurance, trust, private equity funds, banks, other financial institutions, and pension funds.
- Formula: `institution buy amount - institution sell amount`.
- Baseline: median absolute institutional net amount over the configured preceding trading days.
- Boundary: the latest absolute net amount must be at least both the configured baseline multiplier and the configured absolute KRW floor.
- Insufficient data: no signal is emitted without the configured minimum number of preceding records, a positive baseline, a current-day record, and a recent `updatedAt`.
- Direction: a positive result emits `INSTITUTIONAL_NET_BUY_SURGE`; a negative result emits `INSTITUTIONAL_NET_SELL_SURGE`.
- Provisional data: the current-day record may change until the upstream completes its end-of-day update. The evidence and dashboard label it as provisional.
- Scope limitation: this is a whole-market signal. It cannot identify an individual stock, institution, account, beneficial owner, or trading intent.
- Check: confirm the final end-of-day value and compare the market index and foreign/individual flows.

## Evaluated but deferred patterns

Repeated direction reversal, persistent orderbook imbalance, price/orderbook divergence, and close-concentrated movement require durable multi-observation state and more explicit auction/session data. They are documented extension candidates rather than guessed from one snapshot. Same-time baselines have a tested pure calculator with five-minute buckets, mean/median/standard deviation, minimum samples, and fallback; production storage waits for enough multi-day history.
