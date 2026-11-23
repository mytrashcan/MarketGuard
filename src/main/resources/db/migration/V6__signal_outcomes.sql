-- Post-signal price outcome, measured once while short-lived price snapshots still exist.

ALTER TABLE anomaly_record
    ADD COLUMN outcome_status VARCHAR(20),
    ADD COLUMN outcome_base_price NUMERIC(19, 4),
    ADD COLUMN outcome_price NUMERIC(19, 4),
    ADD COLUMN outcome_change_percent NUMERIC(30, 8),
    ADD COLUMN outcome_followed_direction BOOLEAN,
    ADD COLUMN outcome_market_change_percent NUMERIC(30, 8),
    ADD COLUMN outcome_excess_change_percent NUMERIC(30, 8),
    ADD COLUMN outcome_followed_after_market BOOLEAN,
    ADD COLUMN outcome_measured_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_anomaly_outcome_status CHECK (
        outcome_status IS NULL OR outcome_status IN ('MEASURED', 'NO_PRICE_DATA', 'NOT_APPLICABLE')
    ),
    -- IS NOT NULL is explicit: a NULL comparison would make the check unknown, and CHECK accepts unknown.
    ADD CONSTRAINT chk_anomaly_outcome_measured CHECK (
        outcome_status IS DISTINCT FROM 'MEASURED'
        OR (outcome_base_price IS NOT NULL AND outcome_base_price > 0
            AND outcome_price IS NOT NULL AND outcome_price > 0
            AND outcome_change_percent IS NOT NULL)
    );

-- Signals recorded before this migration have no retained snapshots to measure against.
UPDATE anomaly_record
SET outcome_status = CASE WHEN stock_code IN ('KOSPI', 'KOSDAQ') THEN 'NOT_APPLICABLE' ELSE 'NO_PRICE_DATA' END,
    outcome_measured_at = now();

CREATE INDEX idx_anomaly_outcome_pending ON anomaly_record (detected_at) WHERE outcome_status IS NULL;
