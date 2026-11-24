-- Permanent price path after each stock signal: one row per (signal, minutes after the signal).
-- Price snapshots are pruned within hours, so this is the only record that lets later analysis
-- re-evaluate signals with other horizons or thresholds. A row with NULL prices means no snapshot was
-- found for that point; it is kept so the point is not retried and gaps stay visible.

CREATE TABLE signal_price_point (
    anomaly_id            BIGINT        NOT NULL REFERENCES anomaly_record (id) ON DELETE CASCADE,
    offset_minutes        INTEGER       NOT NULL,
    base_price            NUMERIC(19, 4),
    price                 NUMERIC(19, 4),
    market_change_percent NUMERIC(30, 8),
    sector_change_percent NUMERIC(30, 8),
    recorded_at           TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (anomaly_id, offset_minutes),
    CONSTRAINT chk_price_point_offset CHECK (offset_minutes > 0),
    CONSTRAINT chk_price_point_prices CHECK (
        (base_price IS NULL OR base_price > 0) AND (price IS NULL OR price > 0)
    )
);
