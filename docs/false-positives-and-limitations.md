# False positives and limitations

MarketGuard uses public, read-only market data. It cannot observe beneficial ownership, account relationships, order intent, cancellations outside sampled snapshots, private communications, or regulator/exchange surveillance feeds. A signal therefore cannot prove unfair trading, manipulation, investment danger, or investment value.

Common benign explanations include broad market or sector moves, the opening/closing profile, auctions, index rebalancing, expected volatility in designated stocks, disclosed corporate events, and temporary data or liquidity gaps. Operators should record one of these only after checking a public source; the application never creates disclosure/news tags by itself.

Current context availability:

| Context | Status |
|---|---|
| regular-session, opening, closing time tags | available from KST evaluation time |
| active exchange warning / price-limit proximity | available from Toss market data |
| orderbook and price/volume direction | available when the upstream endpoint returns data |
| KOSPI/KOSDAQ market-wide institutional buy/sell amounts | available from Toss investor-trading data; current-day values are provisional |
| sector/market relative return | extension point only; aligned history unavailable |
| same-time historical baseline | calculator tested; production history/cache deferred |
| disclosures, news, new listing, split, ex-dividend | external data required |

The dashboard's “buy/sell” bar is pending bid/ask quantity, not trade-side volume or investor net buying. The official rate shown for ranking members is the Toss `changeRate` paired with the same response's price and base price; fallback daily-candle calculations are visibly labeled.

Institutional-flow signals use the KOSPI or KOSDAQ market-wide institution total. They do not attribute flow to an individual stock or a particular institution. The current-day record can change until the upstream end-of-day update, so operators must confirm the final value before escalation.

Review outcomes are analytics for operator judgment. They do not automatically tune detection thresholds because the sample is biased, labels are subjective, and automated feedback could hide future signals.
