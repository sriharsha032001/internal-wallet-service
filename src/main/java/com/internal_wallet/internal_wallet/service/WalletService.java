package com.internal_wallet.internal_wallet.service;

import com.internal_wallet.internal_wallet.dto.BalanceResponse;
import com.internal_wallet.internal_wallet.dto.LedgerEntryResponse;
import com.internal_wallet.internal_wallet.entity.AssetType;
import com.internal_wallet.internal_wallet.entity.Wallet;
import com.internal_wallet.internal_wallet.exception.AssetTypeNotFoundException;
import com.internal_wallet.internal_wallet.exception.WalletNotFoundException;
import com.internal_wallet.internal_wallet.repository.AssetTypeRepository;
import com.internal_wallet.internal_wallet.repository.LedgerEntryRepository;
import com.internal_wallet.internal_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    /**
     * Returns the current balance for a user's wallet of a given asset type.
     *
     * Complexity: O(1) — two indexed lookups (unique index on asset_types.name,
     * composite index on wallets(user_id, asset_type_id)).
     *
     * Edge cases:
     * - Unknown assetType name  → 404 AssetTypeNotFoundException
     * - No wallet for userId+assetType → 404 WalletNotFoundException
     * - System wallet names (TREASURY/REVENUE) never match because their user_id is NULL
     */
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long userId, String assetTypeName) {
        AssetType assetType = assetTypeRepository.findByName(assetTypeName)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetTypeName));

        Wallet wallet = walletRepository.findByUserIdAndAssetTypeId(userId, assetType.getId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for userId=" + userId + ", assetType=" + assetTypeName));

        return new BalanceResponse(userId, assetType.getName(), wallet.getBalance());
    }

    /**
     * Returns all ledger entries for a user's wallet of a given asset type,
     * sorted most-recent-first.
     *
     * Each entry carries the parent transaction's type and referenceId so the
     * caller gets a self-contained history item without extra joins.
     *
     * Complexity: O(k) where k = number of entries for this wallet.
     * Uses idx_ledger_wallet index (wallet_id) — no full table scan.
     *
     * Edge cases:
     * - Unknown assetType name          → 404 AssetTypeNotFoundException
     * - No wallet for userId+assetType  → 404 WalletNotFoundException
     * - Wallet exists but no entries    → returns empty list (not an error)
     */
    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedger(Long userId, String assetTypeName) {
        AssetType assetType = assetTypeRepository.findByName(assetTypeName)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetTypeName));

        Wallet wallet = walletRepository.findByUserIdAndAssetTypeId(userId, assetType.getId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for userId=" + userId + ", assetType=" + assetTypeName));

        // ORDER BY createdAt DESC is handled in the repository JOIN FETCH query — no in-memory sort needed
        return ledgerEntryRepository.findByWalletId(wallet.getId())
                .stream()
                .map(entry -> LedgerEntryResponse.builder()
                        .ledgerEntryId(entry.getId())
                        .transactionId(entry.getTransaction().getId())
                        .transactionType(entry.getTransaction().getTransactionType().name())
                        .entryType(entry.getEntryType().name())
                        .amount(entry.getAmount())
                        .assetType(entry.getAssetType().getName())
                        .referenceId(entry.getTransaction().getReferenceId())
                        .timestamp(entry.getCreatedAt())
                        .build())
                .toList();
    }
}
