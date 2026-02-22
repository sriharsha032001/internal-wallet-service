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

    // SELECT FOR UPDATE — blocks other transactions from modifying this wallet row
    // caller must lock wallets in ascending ID order to avoid deadlocks
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") Long id);

    // for system wallets (TREASURY, REVENUE)
    Optional<Wallet> findByWalletNameAndAssetTypeId(String walletName, Long assetTypeId);

    // for user wallets
    Optional<Wallet> findByUserIdAndAssetTypeId(Long userId, Long assetTypeId);
}
