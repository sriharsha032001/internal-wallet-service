-- CHECK constraints — catches bad data even if someone runs SQL directly,
-- not just through the application

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_amount_positive
        CHECK (amount > 0);

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entry_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT'));

-- SYSTEM wallets (TREASURY) are allowed to go negative — they're the funding source
-- user wallets must never go below zero
ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_user_balance_non_negative
        CHECK (wallet_type = 'SYSTEM' OR balance >= 0);

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallet_type
        CHECK (wallet_type IN ('USER', 'SYSTEM'));

ALTER TABLE transactions
    ADD CONSTRAINT chk_transaction_type
        CHECK (transaction_type IN ('TOPUP', 'BONUS', 'SPEND'));

-- for queries like: SELECT * FROM ledger_entries WHERE wallet_id = ? AND entry_type = 'DEBIT'
CREATE INDEX idx_ledger_wallet_entry_type
    ON ledger_entries (wallet_id, entry_type);

-- for global reporting across all wallets by type and time
CREATE INDEX idx_ledger_entry_type_created_at
    ON ledger_entries (entry_type, created_at DESC);
