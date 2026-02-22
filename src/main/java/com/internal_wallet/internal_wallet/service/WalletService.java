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

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long userId, String assetTypeName) {
        AssetType assetType = assetTypeRepository.findByName(assetTypeName)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetTypeName));

        Wallet wallet = walletRepository.findByUserIdAndAssetTypeId(userId, assetType.getId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for userId=" + userId + ", assetType=" + assetTypeName));

        return new BalanceResponse(userId, assetType.getName(), wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> getLedger(Long userId, String assetTypeName) {
        AssetType assetType = assetTypeRepository.findByName(assetTypeName)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetTypeName));

        Wallet wallet = walletRepository.findByUserIdAndAssetTypeId(userId, assetType.getId())
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for userId=" + userId + ", assetType=" + assetTypeName));

        // sorting is handled by the query, associations are JOIN FETCHed to avoid N+1
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
