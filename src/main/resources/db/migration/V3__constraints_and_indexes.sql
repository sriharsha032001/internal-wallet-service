-- ============================================================
-- V3__constraints_and_indexes.sql
--
-- ACID Reinforcement at the schema level:
--   Atomicity   → enforced by @Transactional (application layer)
--   Consistency → CHECK constraints added here prevent invalid data
--                 even if application code is bypassed (e.g. direct SQL)
--   Isolation   → pessimistic SELECT FOR UPDATE (application layer)
--   Durability  → PostgreSQL WAL (database engine)
--
-- Indexes for DEBIT / CREDIT reporting queries.
-- ============================================================


-- ---- CHECK constraints: ledger_entries ---------------------------

-- Amounts must always be positive (no zero or negative ledger lines)
ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0);

-- entry_type is an open VARCHAR — constrain to the two legal values
ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entry_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT'));


-- ---- CHECK constraints: wallets ----------------------------------

-- User wallet balances must never go below zero.
-- SYSTEM wallets (TREASURY, REVENUE) are exempt:
--   • TREASURY is debited as credits are issued, so it can go negative.
--   • REVENUE is credit-only but keeping it unconstrained avoids complexity.
-- This is a DB-level safety net: the application already rejects
-- overspend via InsufficientBalanceException (checked after the lock),
-- but the constraint catches any direct SQL bypass.
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_user_balance_non_negative
        CHECK (wallet_type = 'SYSTEM' OR balance >= 0);

-- wallet_type must be one of the two legal values
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_type
        CHECK (wallet_type IN ('USER', 'SYSTEM'));


-- ---- CHECK constraints: transactions -----------------------------

-- transaction_type must match the three legal values
ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_type
        CHECK (transaction_type IN ('TOPUP', 'BONUS', 'SPEND'));


-- ---- Indexes for DEBIT / CREDIT queries --------------------------

-- Covers: "fetch all DEBIT entries for wallet X"
--   e.g. SELECT * FROM ledger_entries WHERE wallet_id = ? AND entry_type = 'DEBIT'
-- Most useful for per-user spend audits and reconciliation reports.
CREATE INDEX idx_ledger_wallet_entry_type
    ON ledger_entries (wallet_id, entry_type);

-- Covers: "fetch all DEBIT entries across all wallets" (global reporting)
--   e.g. SELECT * FROM ledger_entries WHERE entry_type = 'DEBIT'
--        ORDER BY created_at DESC
-- Including created_at supports time-range reporting without a separate sort pass.
CREATE INDEX idx_ledger_entry_type_created_at
    ON ledger_entries (entry_type, created_at DESC);
