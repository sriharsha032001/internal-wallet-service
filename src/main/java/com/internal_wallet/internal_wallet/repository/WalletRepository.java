package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Acquires a PostgreSQL row-level exclusive lock (SELECT … FOR UPDATE) on
     * the wallet row.  All callers within the same transaction will block until
     * competing transactions release their locks, guaranteeing that balance
     * checks and updates are serialised per wallet.
     *
     * Wallets must be locked in ascending ID order by the caller to prevent
     * deadlocks when two concurrent transactions involve the same pair of wallets
     * in opposite order.
     *
     * O(1) — primary key lookup.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);

    /**
     * Used to look up system wallets (TREASURY / REVENUE).
     * O(1) — backed by UNIQUE constraint on (wallet_name, asset_type_id).
     */
    Optional<Wallet> findByWalletNameAndAssetTypeId(String walletName, Long assetTypeId);

    /**
     * Used to look up user wallets by (userId, assetTypeId).
     * O(1) — backed by index idx_wallets_user_asset on (user_id, asset_type_id).
     */
    Optional<Wallet> findByUserIdAndAssetTypeId(Long userId, Long assetTypeId);
}
