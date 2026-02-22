package com.internal_wallet.internal_wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internal_wallet.internal_wallet.dto.BonusRequest;
import com.internal_wallet.internal_wallet.dto.SpendRequest;
import com.internal_wallet.internal_wallet.dto.TopupRequest;
import com.internal_wallet.internal_wallet.dto.TransactionResponse;
import com.internal_wallet.internal_wallet.entity.*;
import com.internal_wallet.internal_wallet.enums.EntryType;
import com.internal_wallet.internal_wallet.enums.TransactionType;
import com.internal_wallet.internal_wallet.exception.AssetTypeNotFoundException;
import com.internal_wallet.internal_wallet.exception.InsufficientBalanceException;
import com.internal_wallet.internal_wallet.exception.WalletNotFoundException;
import com.internal_wallet.internal_wallet.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final WalletRepository walletRepository;
    private final AssetTypeRepository assetTypeRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    // timeout=15 is a backstop — PostgreSQL's lock_timeout (3s) usually fires first
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public TransactionResponse topUp(TopupRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;
        return executeTreasuryToUser(req.getUserId(), req.getAssetType(), req.getAmount(),
                TransactionType.TOPUP, req.getReferenceId(), idempotencyKey);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public TransactionResponse bonus(BonusRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;
        return executeTreasuryToUser(req.getUserId(), req.getAssetType(), req.getAmount(),
                TransactionType.BONUS, req.getReason(), idempotencyKey);
    }

    // shared flow for TOPUP and BONUS — both move money from TREASURY to a user wallet
    // wallets locked in ascending ID order to prevent deadlocks
    private TransactionResponse executeTreasuryToUser(Long userId, String assetTypeName,
                                                      BigDecimal amount, TransactionType type,
                                                      String referenceId, String idempotencyKey) {
        AssetType assetType      = resolveAssetType(assetTypeName);
        Wallet    userWallet     = resolveUserWallet(userId, assetType.getId());
        Wallet    treasuryWallet = resolveSystemWallet("TREASURY", assetType.getId());

        List<Wallet> locked = lockInOrder(treasuryWallet.getId(), userWallet.getId());
        Wallet lockedTreasury = findById(locked, treasuryWallet.getId());
        Wallet lockedUser     = findById(locked, userWallet.getId());

        lockedTreasury.setBalance(lockedTreasury.getBalance().subtract(amount));
        lockedUser.setBalance(lockedUser.getBalance().add(amount));
        walletRepository.saveAll(List.of(lockedTreasury, lockedUser));

        Transaction txn = transactionRepository.save(new Transaction(type, referenceId));
        createLedgerEntries(txn, lockedTreasury, lockedUser, amount, assetType);

        TransactionResponse response = buildResponse(txn, lockedUser, userId, amount, assetType.getName());
        saveIdempotencyKey(idempotencyKey, response);
        return response;
    }

    // balance check must happen AFTER acquiring the row lock — checking before locking
    // opens a TOCTOU race where two concurrent spends both pass the check and overdraw
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 15)
    public TransactionResponse spend(SpendRequest req, String idempotencyKey) {
        TransactionResponse cached = getCachedResponse(idempotencyKey);
        if (cached != null) return cached;

        AssetType assetType    = resolveAssetType(req.getAssetType());
        Wallet    userWallet   = resolveUserWallet(req.getUserId(), assetType.getId());
        Wallet    revenueWallet = resolveSystemWallet("REVENUE", assetType.getId());

        List<Wallet> locked = lockInOrder(userWallet.getId(), revenueWallet.getId());
        Wallet lockedUser    = findById(locked, userWallet.getId());
        Wallet lockedRevenue = findById(locked, revenueWallet.getId());

        if (lockedUser.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance: available=" + lockedUser.getBalance()
                            + ", requested=" + req.getAmount()
                            + ", assetType=" + assetType.getName());
        }

        lockedUser.setBalance(lockedUser.getBalance().subtract(req.getAmount()));
        lockedRevenue.setBalance(lockedRevenue.getBalance().add(req.getAmount()));
        walletRepository.saveAll(List.of(lockedUser, lockedRevenue));

        Transaction txn = transactionRepository.save(
                new Transaction(TransactionType.SPEND, req.getService()));
        createLedgerEntries(txn, lockedUser, lockedRevenue, req.getAmount(), assetType);

        TransactionResponse response = buildResponse(txn, lockedUser, req.getUserId(),
                req.getAmount(), assetType.getName());
        saveIdempotencyKey(idempotencyKey, response);
        return response;
    }

    // runs inside the caller's transaction — if the transaction rolls back,
    // the idempotency key is NOT saved, so the client can safely retry with the same key
    private TransactionResponse getCachedResponse(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .map(ik -> {
                    try {
                        return objectMapper.readValue(ik.getResponseBody(), TransactionResponse.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Failed to deserialize cached idempotency response for key: "
                                        + idempotencyKey, e);
                    }
                })
                .orElse(null);
    }

    // called by the controller after a concurrent duplicate triggers a unique constraint violation
    @Transactional(readOnly = true)
    public java.util.Optional<TransactionResponse> resolveConflict(String idempotencyKey) {
        return idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey)
                .map(ik -> {
                    try {
                        return objectMapper.readValue(ik.getResponseBody(), TransactionResponse.class);
                    } catch (JsonProcessingException e) {
                        throw new IllegalStateException(
                                "Failed to deserialize idempotency response for key: " + idempotencyKey, e);
                    }
                });
    }

    private void saveIdempotencyKey(String key, TransactionResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            idempotencyKeyRepository.save(new IdempotencyKey(key, json, 200));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotency response", e);
        }
    }

    private AssetType resolveAssetType(String name) {
        return assetTypeRepository.findByName(name)
                .orElseThrow(() -> new AssetTypeNotFoundException(name));
    }

    private Wallet resolveUserWallet(Long userId, Long assetTypeId) {
        return walletRepository.findByUserIdAndAssetTypeId(userId, assetTypeId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "User wallet not found for userId=" + userId));
    }

    // missing system wallet = infra problem, not a client error
    private Wallet resolveSystemWallet(String name, Long assetTypeId) {
        return walletRepository.findByWalletNameAndAssetTypeId(name, assetTypeId)
                .orElseThrow(() -> new IllegalStateException(
                        "System wallet '" + name + "' not found for assetTypeId=" + assetTypeId
                                + ". Check seed data."));
    }

    // always lock in ascending ID order — prevents deadlocks when two transactions
    // touch the same pair of wallets in opposite order
    private List<Wallet> lockInOrder(Long id1, Long id2) {
        List<Long> sortedIds = new ArrayList<>(List.of(id1, id2));
        sortedIds.sort(Long::compareTo);

        List<Wallet> result = new ArrayList<>();
        for (Long id : sortedIds) {
            Wallet locked = walletRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new WalletNotFoundException("Wallet not found with id=" + id));
            result.add(locked);
        }
        return result;
    }

    private Wallet findById(List<Wallet> wallets, Long id) {
        return wallets.stream()
                .filter(w -> w.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked wallet with id=" + id + " not found in list"));
    }

    // one DEBIT + one CREDIT per transaction — the ledger is the source of truth,
    // the balance column is just a cached total for fast reads
    private void createLedgerEntries(Transaction txn, Wallet debitWallet, Wallet creditWallet,
                                     BigDecimal amount, AssetType assetType) {
        ledgerEntryRepository.saveAll(List.of(
                new LedgerEntry(txn, debitWallet,  EntryType.DEBIT,  amount, assetType),
                new LedgerEntry(txn, creditWallet, EntryType.CREDIT, amount, assetType)
        ));
    }

    private TransactionResponse buildResponse(Transaction txn, Wallet userWallet,
                                               Long userId, BigDecimal amount, String assetTypeName) {
        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .userId(userId)
                .assetType(assetTypeName)
                .amount(amount)
                .transactionType(txn.getTransactionType().name())
                .newBalance(userWallet.getBalance())
                .timestamp(LocalDateTime.now(ZoneId.of("Asia/Kolkata")))
                .build();
    }
}
