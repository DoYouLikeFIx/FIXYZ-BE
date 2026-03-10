ALTER TABLE member
  MODIFY COLUMN member_no VARCHAR(64) NOT NULL;

ALTER TABLE member
  ADD CONSTRAINT uk_member_member_no UNIQUE (member_no);

ALTER TABLE member
  ADD CONSTRAINT uk_member_email UNIQUE (email);

ALTER TABLE accounts
  ADD COLUMN member_id BIGINT;

UPDATE accounts
SET member_id = (
  SELECT m.id
  FROM member m
  WHERE m.member_no = accounts.member_no
)
WHERE member_id IS NULL;

ALTER TABLE accounts
  MODIFY COLUMN member_id BIGINT NOT NULL;

ALTER TABLE accounts
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE accounts
  MODIFY COLUMN account_no VARCHAR(14) NOT NULL;

ALTER TABLE accounts
  MODIFY COLUMN cash_balance DECIMAL(19, 4) NOT NULL;

ALTER TABLE accounts
  MODIFY COLUMN daily_sell_limit DECIMAL(19, 4) NOT NULL;

ALTER TABLE accounts
  ADD CONSTRAINT uk_accounts_member_id UNIQUE (member_id);

ALTER TABLE accounts
  ADD CONSTRAINT fk_accounts_member_id FOREIGN KEY (member_id) REFERENCES member(id);

ALTER TABLE accounts
  ADD CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'));

ALTER TABLE accounts
  ADD CONSTRAINT ck_accounts_account_no_numeric CHECK (account_no REGEXP '^[0-9]{10,14}$');

ALTER TABLE accounts
  ADD CONSTRAINT ck_accounts_cash_balance_non_negative CHECK (cash_balance >= 0);

ALTER TABLE accounts
  ADD CONSTRAINT ck_accounts_daily_sell_limit_positive CHECK (daily_sell_limit > 0);

ALTER TABLE accounts
  DROP COLUMN member_no;
