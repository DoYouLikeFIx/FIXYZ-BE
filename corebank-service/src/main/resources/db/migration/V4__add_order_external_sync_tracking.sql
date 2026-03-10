ALTER TABLE orders ADD COLUMN external_sync_status VARCHAR(20) NULL;
ALTER TABLE orders ADD COLUMN fep_reference_id VARCHAR(64) NULL;
ALTER TABLE orders ADD COLUMN failure_reason VARCHAR(255) NULL;
