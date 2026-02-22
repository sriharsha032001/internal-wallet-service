package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // JOIN FETCH to avoid N+1 — without it, every entry.getTransaction() and
    // entry.getAssetType() would fire a separate query
    @Query("SELECT e FROM LedgerEntry e " +
           "JOIN FETCH e.transaction " +
           "JOIN FETCH e.assetType " +
           "WHERE e.wallet.id = :walletId " +
           "ORDER BY e.createdAt DESC")
    List<LedgerEntry> findByWalletId(@Param("walletId") Long walletId);

    // always returns exactly 2 rows (one DEBIT, one CREDIT)
    List<LedgerEntry> findByTransactionId(Long transactionId);
}
