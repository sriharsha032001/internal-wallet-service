package com.internal_wallet.internal_wallet.controller;

import com.internal_wallet.internal_wallet.dto.BalanceResponse;
import com.internal_wallet.internal_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * GET /api/v1/wallets/{userId}/balance?assetType=GOLD_COINS
     *
     * Returns the current balance for the given user and asset type.
     *
     * Errors:
     *   404 — unknown userId/assetType combination
     *   404 — unknown assetType name
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long userId,
            @RequestParam String assetType) {

        return ResponseEntity.ok(walletService.getBalance(userId, assetType));
    }
}
