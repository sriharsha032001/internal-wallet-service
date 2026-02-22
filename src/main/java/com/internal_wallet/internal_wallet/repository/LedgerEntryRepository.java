package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Returns all ledger entries for a given wallet, most-recent-first.
     *
     * JOIN FETCH eagerly loads the LAZY `transaction` and `assetType` associations
     * in a single SQL query.  Without this, accessing entry.getTransaction() or
     * entry.getAssetType() inside the stream map would fire one extra SELECT per
     * entry (N+1 problem — 100 entries → 201 queries instead of 1).
     *
     * ORDER BY in JPQL lets the DB use idx_ledger_wallet (wallet_id) and avoids
     * an in-memory sort in the service layer.
     *
     * O(k) where k = number of entries for this wallet.
     */
    @Query("SELECT e FROM LedgerEntry e " +
           "JOIN FETCH e.transaction " +
           "JOIN FETCH e.assetType " +
           "WHERE e.wallet.id = :walletId " +
           "ORDER BY e.createdAt DESC")
    List<LedgerEntry> findByWalletId(@Param("walletId") Long walletId);

    /**
     * Returns the two ledger entries that make up a logical transaction.
     * O(1) given the index on transaction_id; always returns exactly 2 rows.
     */
    List<LedgerEntry> findByTransactionId(Long transactionId);
}
