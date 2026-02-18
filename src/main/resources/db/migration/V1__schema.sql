-- ============================================================
-- V1__schema.sql  —  Internal Wallet Service Schema
-- ============================================================

CREATE TABLE asset_types (
    id         BIGSERIAL    PRIMARY KEY,
    name       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asset_types_name UNIQUE (name)
);

CREATE TABLE wallets (
    id            BIGSERIAL      PRIMARY KEY,
    user_id       BIGINT,                          -- NULL for system wallets (TREASURY, REVENUE)
    wallet_type   VARCHAR(10)    NOT NULL,          -- USER | SYSTEM
    wallet_name   VARCHAR(50)    NOT NULL,          -- TREASURY | REVENUE | USER_1 | USER_2 ...
    asset_type_id BIGINT         NOT NULL REFERENCES asset_types (id),
    balance       NUMERIC(20, 6) NOT NULL DEFAULT 0,
    version       BIGINT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wallets_name_asset UNIQUE (wallet_name, asset_type_id)
);

CREATE TABLE transactions (
    id               BIGSERIAL    PRIMARY KEY,
    transaction_type VARCHAR(10)  NOT NULL,         -- TOPUP | BONUS | SPEND
    reference_id     VARCHAR(255),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE ledger_entries (
    id             BIGSERIAL      PRIMARY KEY,
    transaction_id BIGINT         NOT NULL REFERENCES transactions (id),
    wallet_id      BIGINT         NOT NULL REFERENCES wallets (id),
    entry_type     VARCHAR(6)     NOT NULL,         -- DEBIT | CREDIT
    amount         NUMERIC(20, 6) NOT NULL,
    asset_type_id  BIGINT         NOT NULL REFERENCES asset_types (id),
    created_at     TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE idempotency_keys (
    id              BIGSERIAL    PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL,
    response_body   TEXT         NOT NULL,
    http_status     INT          NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key)
);

-- ---- Indexes ------------------------------------------------
-- Wallets: fast lookup by (user_id, asset_type_id) — used in every transaction
CREATE INDEX idx_wallets_user_asset ON wallets (user_id, asset_type_id);

-- Ledger: fast lookup of all entries for a wallet (balance auditing)
CREATE INDEX idx_ledger_wallet     ON ledger_entries (wallet_id);

-- Ledger: fast lookup of all entries for a transaction (2 per txn)
CREATE INDEX idx_ledger_txn        ON ledger_entries (transaction_id);
