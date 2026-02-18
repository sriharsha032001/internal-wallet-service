-- ============================================================
-- V2__seed.sql  —  Seed asset types, system wallets, users,
--                  and initial balances via double-entry ledger
-- ============================================================

-- 1. Asset types
INSERT INTO asset_types (name) VALUES
    ('GOLD_COINS'),
    ('DIAMONDS'),
    ('LOYALTY_POINTS');

-- 2. TREASURY wallets — one per asset type (large reserve)
INSERT INTO wallets (wallet_type, wallet_name, asset_type_id, balance)
SELECT 'SYSTEM', 'TREASURY', id, 1000000
FROM asset_types;

-- 3. REVENUE wallets — one per asset type (collect spend)
INSERT INTO wallets (wallet_type, wallet_name, asset_type_id, balance)
SELECT 'SYSTEM', 'REVENUE', id, 0
FROM asset_types;

-- 4. User wallets — users 1 & 2, each with all 3 asset types (start at 0)
INSERT INTO wallets (user_id, wallet_type, wallet_name, asset_type_id, balance)
SELECT 1, 'USER', 'USER_1', id, 0
FROM asset_types;

INSERT INTO wallets (user_id, wallet_type, wallet_name, asset_type_id, balance)
SELECT 2, 'USER', 'USER_2', id, 0
FROM asset_types;

-- ============================================================
-- 5. Seed initial balances via double-entry ledger entries
--    User 1: GOLD_COINS=100, DIAMONDS=50, LOYALTY_POINTS=200
--    User 2: GOLD_COINS=200, DIAMONDS=100, LOYALTY_POINTS=500
-- ============================================================

-- ---- User 1 · GOLD_COINS = 100 ----------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'GOLD_COINS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_1'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER1_GOLD') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  100, at_id),
        (txn_id, dst_id, 'CREDIT', 100, at_id);

    UPDATE wallets SET balance = balance - 100 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 100 WHERE id = dst_id;
END $$;

-- ---- User 1 · DIAMONDS = 50 --------------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'DIAMONDS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_1'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER1_DIAMONDS') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  50, at_id),
        (txn_id, dst_id, 'CREDIT', 50, at_id);

    UPDATE wallets SET balance = balance - 50 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 50 WHERE id = dst_id;
END $$;

-- ---- User 1 · LOYALTY_POINTS = 200 ------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'LOYALTY_POINTS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_1'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER1_LOYALTY') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  200, at_id),
        (txn_id, dst_id, 'CREDIT', 200, at_id);

    UPDATE wallets SET balance = balance - 200 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 200 WHERE id = dst_id;
END $$;

-- ---- User 2 · GOLD_COINS = 200 ----------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'GOLD_COINS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_2'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER2_GOLD') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  200, at_id),
        (txn_id, dst_id, 'CREDIT', 200, at_id);

    UPDATE wallets SET balance = balance - 200 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 200 WHERE id = dst_id;
END $$;

-- ---- User 2 · DIAMONDS = 100 --------------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'DIAMONDS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_2'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER2_DIAMONDS') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  100, at_id),
        (txn_id, dst_id, 'CREDIT', 100, at_id);

    UPDATE wallets SET balance = balance - 100 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 100 WHERE id = dst_id;
END $$;

-- ---- User 2 · LOYALTY_POINTS = 500 ------------------------
DO $$
DECLARE
    at_id   BIGINT;
    src_id  BIGINT;
    dst_id  BIGINT;
    txn_id  BIGINT;
BEGIN
    SELECT id INTO at_id  FROM asset_types WHERE name = 'LOYALTY_POINTS';
    SELECT id INTO src_id FROM wallets WHERE wallet_name = 'TREASURY' AND asset_type_id = at_id;
    SELECT id INTO dst_id FROM wallets WHERE wallet_name = 'USER_2'   AND asset_type_id = at_id;

    INSERT INTO transactions (transaction_type, reference_id)
    VALUES ('TOPUP', 'SEED_USER2_LOYALTY') RETURNING id INTO txn_id;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, asset_type_id) VALUES
        (txn_id, src_id, 'DEBIT',  500, at_id),
        (txn_id, dst_id, 'CREDIT', 500, at_id);

    UPDATE wallets SET balance = balance - 500 WHERE id = src_id;
    UPDATE wallets SET balance = balance + 500 WHERE id = dst_id;
END $$;
