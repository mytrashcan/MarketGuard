-- Optional sector benchmark for post-signal outcomes (filled only when outcome.sectors-file is configured).
-- A separate migration: V6 is already released, and editing it would break Flyway checksum validation.

ALTER TABLE anomaly_record
    ADD COLUMN outcome_sector_change_percent NUMERIC(30, 8),
    ADD COLUMN outcome_followed_after_sector BOOLEAN;
