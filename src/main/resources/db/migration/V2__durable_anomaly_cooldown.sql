CREATE TABLE anomaly_cooldown (
    stock_code      VARCHAR(20) NOT NULL,
    rule_type       VARCHAR(40) NOT NULL,
    last_emitted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (stock_code, rule_type)
);

ALTER TABLE price_snapshot
    ADD CONSTRAINT chk_snapshot_stock_code CHECK (stock_code ~ '^[0-9]{6}$'),
    ADD CONSTRAINT chk_snapshot_price_positive CHECK (price > 0);

ALTER TABLE anomaly_record
    ADD CONSTRAINT chk_anomaly_stock_code CHECK (stock_code ~ '^[0-9]{6}$');
