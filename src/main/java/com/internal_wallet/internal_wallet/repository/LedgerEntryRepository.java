package com.internal_wallet.internal_wallet.repository;

import com.internal_wallet.internal_wallet.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /**
     * Returns all ledger entries for a given wallet (for auditing / balance reconciliation).
     * O(k) where k = number of entries for this wallet.
     */
    List<LedgerEntry> findByWalletId(Long walletId);

    /**
     * Returns the two ledger entries that make up a logical transaction.
     * O(1) given the index on transaction_id; always returns exactly 2 rows.
     */
    List<LedgerEntry> findByTransactionId(Long transactionId);
}
