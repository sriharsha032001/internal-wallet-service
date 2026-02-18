package com.internal_wallet.internal_wallet.service;

import com.internal_wallet.internal_wallet.dto.BalanceResponse;
import com.internal_wallet.internal_wallet.entity.AssetType;
import com.internal_wallet.internal_wallet.entity.Wallet;
import com.internal_wallet.internal_wallet.exception.AssetTypeNotFoundException;
import com.internal_wallet.internal_wallet.exception.WalletNotFoundException;
import com.internal_wallet.internal_wallet.repository.AssetTypeRepository;
import com.internal_wallet.internal_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final AssetTypeRepository assetTypeRepository;

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
}
