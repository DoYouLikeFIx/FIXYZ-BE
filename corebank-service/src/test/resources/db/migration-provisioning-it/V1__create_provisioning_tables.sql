CREATE TABLE member (
  id BIGINT PRIMARY KEY,
  member_no VARCHAR(64) NOT NULL,
  email VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_member_member_no UNIQUE (member_no),
  CONSTRAINT uk_member_email UNIQUE (email)
);

CREATE TABLE accounts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  account_no VARCHAR(14) NOT NULL,
  member_id BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  currency VARCHAR(16) NOT NULL,
  cash_balance DECIMAL(19, 4) NOT NULL,
  daily_sell_limit DECIMAL(19, 4) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version BIGINT,
  CONSTRAINT fk_accounts_member FOREIGN KEY (member_id) REFERENCES member (id),
  CONSTRAINT uk_accounts_account_no UNIQUE (account_no),
  CONSTRAINT uk_accounts_member_id UNIQUE (member_id),
  CONSTRAINT chk_accounts_account_no_numeric CHECK (account_no REGEXP '^[0-9]{14}$')
);
