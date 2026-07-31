# Composite anomaly score

The score is a transparent review-priority heuristic. It is not a probability of manipulation, unfair trading, loss, or investment risk.

Default contributions:

| Rule | Points |
|---|---:|
| `PRICE_SPIKE` | 25 |
| `VOLUME_SURGE` | 25 |
| `ORDERBOOK_IMBALANCE` | 20 |
| `PRICE_LIMIT` | 15 |
| `INVESTMENT_WARNING` | 15 |
| `PRICE_VOLUME_SURGE` | 35 |
| `INSTITUTIONAL_NET_BUY_SURGE` | 20 |
| `INSTITUTIONAL_NET_SELL_SURGE` | 20 |

Each distinct rule contributes at most once per case. Two or more distinct rules add the configured 10-point simultaneous-signal bonus. The result is capped at the configured maximum, normally 100.

Default attention labels are `LOW` below 30, `MEDIUM` from 30 to 59, and `HIGH` from 60. Configuration lives under `surveillance.rule-weights`, `simultaneous-signal-bonus`, `max-score`, `medium-threshold`, and `high-threshold`.

The API returns every rule contribution with its points, summary, observed value, baseline, threshold, and unit, plus the bonus and a calculation explanation. Historical operator outcomes never automatically modify weights or thresholds.
