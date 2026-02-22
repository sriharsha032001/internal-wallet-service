package com.internal_wallet.internal_wallet.controller;

import com.internal_wallet.internal_wallet.dto.BalanceResponse;
import com.internal_wallet.internal_wallet.dto.LedgerEntryResponse;
import com.internal_wallet.internal_wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * GET /api/v1/wallets/{userId}/ledger?assetType=GOLD_COINS
     *
     * Returns all ledger entries for the given user + asset type,
     * sorted most-recent-first. Each entry includes the parent
     * transaction type and reference so the FE can render a
     * full transaction history without further calls.
     *
     * Errors:
     *   404 — unknown assetType name
     *   404 — unknown userId/assetType combination
     */
    @GetMapping("/{userId}/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedger(
            @PathVariable Long userId,
            @RequestParam String assetType) {

        return ResponseEntity.ok(walletService.getLedger(userId, assetType));
    }
}
