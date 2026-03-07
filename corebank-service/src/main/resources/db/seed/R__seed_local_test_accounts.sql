DELETE FROM ledger_entry_refs;
DELETE FROM ledger_entries;
DELETE FROM journal_entries;
DELETE FROM executions;
DELETE FROM orders;
DELETE FROM positions;
DELETE FROM accounts;
DELETE FROM member;

INSERT INTO member (id, member_no, email) VALUES (1, 'M-1001', 'seed@fix.local');

INSERT INTO accounts (id, account_no, member_no, currency, cash_balance, daily_sell_limit, created_at, updated_at, version)
VALUES (1, 'ACC-1001', 'M-1001', 'KRW', 100000000.0000, 500.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO positions (id, account_id, symbol, qty, avg_price, created_at, updated_at, version)
VALUES (1, 1, '005930', 120.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);

INSERT INTO executions (id, order_id, account_id, cl_ord_id, symbol, side, exec_qty, exec_price, executed_at, created_at, updated_at, version)
VALUES (1, 0, 1, 'CL-SEED-SELL-001', '005930', 'SELL', 100.0000, 70000.0000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0);
