-- Allow market-level KOSPI/KOSDAQ signals while preserving the six-digit stock boundary.

ALTER TABLE anomaly_record
    DROP CONSTRAINT chk_anomaly_stock_code,
    DROP CONSTRAINT chk_anomaly_rule_type,
    ALTER COLUMN stock_code TYPE VARCHAR(20),
    ADD CONSTRAINT chk_anomaly_stock_code CHECK (
        stock_code ~ '^[0-9]{6}$' OR stock_code IN ('KOSPI', 'KOSDAQ')
    ),
    ADD CONSTRAINT chk_anomaly_rule_type CHECK (
        rule_type IN (
            'PRICE_LIMIT', 'PRICE_SPIKE', 'VOLUME_SURGE', 'ORDERBOOK_IMBALANCE',
            'INVESTMENT_WARNING', 'PRICE_VOLUME_SURGE',
            'INSTITUTIONAL_NET_BUY_SURGE', 'INSTITUTIONAL_NET_SELL_SURGE'
        )
    );

ALTER TABLE anomaly_cooldown
    DROP CONSTRAINT chk_cooldown_stock_code,
    DROP CONSTRAINT chk_cooldown_rule_type,
    ALTER COLUMN stock_code TYPE VARCHAR(20),
    ADD CONSTRAINT chk_cooldown_stock_code CHECK (
        stock_code ~ '^[0-9]{6}$' OR stock_code IN ('KOSPI', 'KOSDAQ')
    ),
    ADD CONSTRAINT chk_cooldown_rule_type CHECK (
        rule_type IN (
            'PRICE_LIMIT', 'PRICE_SPIKE', 'VOLUME_SURGE', 'ORDERBOOK_IMBALANCE',
            'INVESTMENT_WARNING', 'PRICE_VOLUME_SURGE',
            'INSTITUTIONAL_NET_BUY_SURGE', 'INSTITUTIONAL_NET_SELL_SURGE'
        )
    );

ALTER TABLE surveillance_case
    DROP CONSTRAINT chk_case_stock_code,
    ADD CONSTRAINT chk_case_stock_code CHECK (
        stock_code ~ '^[0-9]{6}$' OR stock_code IN ('KOSPI', 'KOSDAQ')
    );

ALTER TABLE case_group_lock
    DROP CONSTRAINT chk_case_lock_stock_code,
    ADD CONSTRAINT chk_case_lock_stock_code CHECK (
        stock_code ~ '^[0-9]{6}$' OR stock_code IN ('KOSPI', 'KOSDAQ')
    );
