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

    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable Long userId,
            @RequestParam String assetType) {

        return ResponseEntity.ok(walletService.getBalance(userId, assetType));
    }

    @GetMapping("/{userId}/ledger")
    public ResponseEntity<List<LedgerEntryResponse>> getLedger(
            @PathVariable Long userId,
            @RequestParam String assetType) {

        return ResponseEntity.ok(walletService.getLedger(userId, assetType));
    }
}
