ALTER TABLE members
  ADD COLUMN account_id BIGINT NULL;

ALTER TABLE members
  ADD COLUMN account_number VARCHAR(14) NULL;
