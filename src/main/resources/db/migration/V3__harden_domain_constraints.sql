ALTER TABLE price_snapshot
    ALTER COLUMN stock_code TYPE VARCHAR(6);

ALTER TABLE anomaly_record
    ALTER COLUMN stock_code TYPE VARCHAR(6),
    ADD CONSTRAINT chk_anomaly_message_not_blank CHECK (length(trim(message)) > 0),
    ADD CONSTRAINT chk_anomaly_rule_type CHECK (
        rule_type IN ('PRICE_SPIKE', 'PRICE_LIMIT', 'ORDERBOOK_IMBALANCE', 'VOLUME_SURGE', 'INVESTMENT_WARNING')
    ),
    ADD CONSTRAINT chk_anomaly_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'));

ALTER TABLE anomaly_cooldown
    ALTER COLUMN stock_code TYPE VARCHAR(6),
    ADD CONSTRAINT chk_cooldown_stock_code CHECK (stock_code ~ '^[0-9]{6}$'),
    ADD CONSTRAINT chk_cooldown_rule_type CHECK (
        rule_type IN ('PRICE_SPIKE', 'PRICE_LIMIT', 'ORDERBOOK_IMBALANCE', 'VOLUME_SURGE', 'INVESTMENT_WARNING')
    );

ALTER TABLE audit_log
    ADD CONSTRAINT chk_audit_action_not_blank CHECK (length(trim(action)) > 0),
    ADD CONSTRAINT chk_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE')),
    ADD CONSTRAINT chk_audit_latency_non_negative CHECK (latency_ms >= 0);
